package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.service.AdminService;
import labchat.service.ChatService;
import labchat.service.RoomService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;

import java.io.IOException;
import java.util.Map;

public final class AdminHandler extends BaseHandler implements HttpHandler {
    private final AdminService adminService;
    private final UserService userService;
    private final RoomService roomService;
    private final ChatService chatService;

    public AdminHandler(AdminService adminService, UserService userService, RoomService roomService, ChatService chatService) {
        this.adminService = adminService;
        this.userService = userService;
        this.roomService = roomService;
        this.chatService = chatService;
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
            if ("/api/server/status".equals(path)) {
                handleServerStatus(exchange);
                return;
            }

            // Verify admin token
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7).trim();
            }

            Map<String, Object> body = null;
            if ("POST".equalsIgnoreCase(method)) {
                body = JsonUtil.parseObject(readBody(exchange));
                if (token == null) {
                    token = JsonUtil.getString(body, "token", null);
                }
            }

            if (!adminService.authenticate(token)) {
                sendError(exchange, 401, "UNAUTHORIZED", "Invalid or missing administrator token.");
                return;
            }

            switch (path) {
                case "/api/admin/verify" -> sendSuccess(exchange, "{\"authenticated\":true}");
                case "/api/admin/kick" -> handleKick(exchange, body);
                case "/api/admin/mute" -> handleMute(exchange, body);
                case "/api/admin/ban" -> handleBan(exchange, body);
                case "/api/admin/clear-room" -> handleClearRoom(exchange, body);
                case "/api/admin/announcement" -> handleAnnouncement(exchange, body);
                default -> sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("ADMIN", "Error processing " + path, e);
            sendError(exchange, 500, "SERVER_ERROR", "Internal admin error: " + e.getMessage());
        }
    }

    private void handleServerStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        sendSuccess(exchange, adminService.getStatusJson());
    }

    private void handleKick(HttpExchange exchange, Map<String, Object> body) throws IOException {
        String target = JsonUtil.getString(body, "targetUser", "").trim();
        if (target.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Target user required.");
            return;
        }
        boolean ok = adminService.kickUser(target);
        if (ok) {
            chatService.addSystemMessage("#general", "⚠️ " + target + " was kicked by administrator");
            Logger.info("ADMIN", "Kicked " + target);
        }
        sendSuccess(exchange, "{\"kicked\":" + ok + "}");
    }

    private void handleMute(HttpExchange exchange, Map<String, Object> body) throws IOException {
        String target = JsonUtil.getString(body, "targetUser", "").trim();
        boolean muted = JsonUtil.getBoolean(body, "muted", true);
        boolean ok = adminService.muteUser(target, muted);
        if (ok) {
            chatService.addSystemMessage("#general", "⚠️ " + target + " was " + (muted ? "muted" : "unmuted") + " by administrator");
            Logger.info("ADMIN", (muted ? "Muted " : "Unmuted ") + target);
        }
        sendSuccess(exchange, "{\"muted\":" + muted + "}");
    }

    private void handleBan(HttpExchange exchange, Map<String, Object> body) throws IOException {
        String target = JsonUtil.getString(body, "targetUser", "").trim();
        int minutes = JsonUtil.getInteger(body, "minutes", 10);
        boolean ok = adminService.banUser(target, minutes);
        if (ok) {
            chatService.addSystemMessage("#general", "🚫 " + target + " was banned for " + minutes + " minutes by administrator");
            Logger.info("ADMIN", "Banned " + target + " for " + minutes + " mins");
        }
        sendSuccess(exchange, "{\"banned\":true}");
    }

    private void handleClearRoom(HttpExchange exchange, Map<String, Object> body) throws IOException {
        String room = JsonUtil.getString(body, "room", "").trim();
        if (room.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Room is required.");
            return;
        }
        chatService.clearRoom(room);
        chatService.addSystemMessage(room, "🧹 Room messages were cleared by administrator");
        Logger.info("ADMIN", "Cleared room " + room);
        sendSuccess(exchange, "{\"cleared\":true}");
    }

    private void handleAnnouncement(HttpExchange exchange, Map<String, Object> body) throws IOException {
        String message = JsonUtil.getString(body, "message", "").trim();
        boolean clear = JsonUtil.getBoolean(body, "clear", false);
        if (clear) {
            adminService.clearAnnouncement();
            Logger.info("ADMIN", "Cleared announcement");
            sendSuccess(exchange, "{\"cleared\":true}");
        } else {
            if (message.isEmpty()) {
                sendError(exchange, 400, "INVALID_REQUEST", "Announcement message cannot be empty.");
                return;
            }
            adminService.postAnnouncement(message);
            Logger.info("ADMIN", "Announcement: " + message);
            sendSuccess(exchange, "{\"announced\":true}");
        }
    }
}
