package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.config.ServerConfig;
import labchat.model.User;
import labchat.service.ChatService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;
import labchat.util.RateLimiter;
import labchat.util.ValidationUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class UserHandler extends BaseHandler implements HttpHandler {
    private final ServerConfig config;
    private final UserService userService;
    private final ChatService chatService;
    private final RateLimiter joinRateLimiter;

    public UserHandler(ServerConfig config, UserService userService, ChatService chatService) {
        this.config = config;
        this.userService = userService;
        this.chatService = chatService;
        this.joinRateLimiter = new RateLimiter(10, 60_000); // 10 joins per min per IP
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            handleOptions(exchange);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            switch (path) {
                case "/api/join" -> handleJoin(exchange);
                case "/api/guest" -> handleGuest(exchange);
                case "/api/leave" -> handleLeave(exchange);
                case "/api/users" -> handleGetUsers(exchange);
                case "/api/presence" -> handlePresence(exchange);
                default -> sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("USER", "Error processing " + path, e);
            sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
        }
    }

    private void handleGuest(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod()) && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        String guestName = userService.registerGuest();
        boolean joined = userService.join(guestName);
        if (joined) {
            chatService.addSystemMessage("#general", "🟢 " + guestName + " joined the chat");
            Logger.info("JOIN", guestName + " (Guest)");
            sendSuccess(exchange, "{\"username\":\"" + JsonUtil.escape(guestName) + "\"}");
        } else {
            sendError(exchange, 500, "GUEST_FAILED", "Failed to register guest user.");
        }
    }

    private void handleJoin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!joinRateLimiter.allow(clientIp)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "Too many join attempts. Please slow down.");
            return;
        }

        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String username = JsonUtil.getString(body, "username", "").trim();

        String validationError = ValidationUtil.validateUsername(username, config.getMaxUsernameLength());
        if (validationError != null) {
            sendError(exchange, 400, "INVALID_USERNAME", validationError);
            return;
        }

        User existing = userService.getUser(username);
        boolean isRejoin = (existing != null);

        if (!userService.join(username)) {
            if (existing != null && existing.isBanned()) {
                sendError(exchange, 403, "USER_BANNED", "This user is currently banned from the chat.");
            } else {
                sendError(exchange, 409, "USERNAME_TAKEN", "Username is already in use or chat is full.");
            }
            return;
        }

        if (!isRejoin) {
            chatService.addSystemMessage("#general", "🟢 " + username + " joined the chat");
            Logger.info("JOIN", username);
        } else {
            Logger.info("REJOIN", username);
        }

        sendSuccess(exchange, "{\"username\":\"" + JsonUtil.escape(username) + "\"}");
    }

    private void handleLeave(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String username = JsonUtil.getString(body, "username", "").trim();
        if (!username.isEmpty()) {
            boolean removed = userService.leave(username);
            if (removed) {
                chatService.addSystemMessage("#general", "🔴 " + username + " left the chat");
                Logger.info("LEAVE", username);
            }
        }
        sendSuccess(exchange, "{}");
    }

    private void handleGetUsers(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        List<User> users = userService.getAllUsers();
        StringBuilder json = new StringBuilder("{\"total\":").append(users.size()).append(",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) json.append(",");
            json.append(users.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handlePresence(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String username = JsonUtil.getString(body, "username", "").trim();
        String status = JsonUtil.getString(body, "status", "online").trim();
        String currentRoom = JsonUtil.getString(body, "currentRoom", "#general").trim();

        if (username.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Username is required.");
            return;
        }

        User u = userService.getUser(username);
        if (u == null) {
            sendError(exchange, 404, "USER_NOT_FOUND", "User not found or session expired.");
            return;
        }

        u.touch();
        u.setStatus(status);
        if (!currentRoom.isEmpty()) {
            u.setCurrentRoom(currentRoom);
        }
        sendSuccess(exchange, "{\"status\":\"" + JsonUtil.escape(u.getStatus()) + "\"}");
    }
}
