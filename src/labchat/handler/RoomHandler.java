package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.model.ChatRoom;
import labchat.service.ChatService;
import labchat.service.RoomService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;
import labchat.util.RateLimiter;
import labchat.util.ValidationUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class RoomHandler extends BaseHandler implements HttpHandler {
    private final RoomService roomService;
    private final UserService userService;
    private final ChatService chatService;
    private final RateLimiter createRateLimiter;

    public RoomHandler(RoomService roomService, UserService userService, ChatService chatService) {
        this.roomService = roomService;
        this.userService = userService;
        this.chatService = chatService;
        this.createRateLimiter = new RateLimiter(5, 60_000); // 5 room creations per min
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
                case "/api/rooms" -> handleGetRooms(exchange);
                case "/api/rooms/create" -> handleCreateRoom(exchange);
                case "/api/rooms/join" -> handleJoinRoom(exchange);
                case "/api/rooms/leave" -> handleLeaveRoom(exchange);
                case "/api/rooms/delete" -> handleDeleteRoom(exchange);
                default -> sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("ROOM", "Error processing " + path, e);
            sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
        }
    }

    private void handleGetRooms(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        List<ChatRoom> rooms = roomService.getAllRooms();
        StringBuilder json = new StringBuilder("{\"rooms\":[");
        for (int i = 0; i < rooms.size(); i++) {
            if (i > 0) json.append(",");
            json.append(rooms.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handleCreateRoom(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String name = JsonUtil.getString(body, "name", "").trim();
        String creator = JsonUtil.getString(body, "creator", "").trim();

        if (!createRateLimiter.allow(creator)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "Room creation rate limit exceeded. Please wait.");
            return;
        }

        String valError = ValidationUtil.validateRoomName(name);
        if (valError != null) {
            sendError(exchange, 400, "INVALID_ROOM_NAME", valError);
            return;
        }

        String formattedName = name.startsWith("#") ? name : "#" + name;
        boolean ok = roomService.createRoom(formattedName, creator);
        if (!ok) {
            sendError(exchange, 409, "ROOM_EXISTS", "Room already exists or cannot be created.");
            return;
        }

        chatService.addSystemMessage(formattedName, "Room " + formattedName + " created by " + creator);
        Logger.info("ROOM", "Created " + formattedName + " by " + creator);
        sendSuccess(exchange, "{\"room\":\"" + JsonUtil.escape(formattedName) + "\"}");
    }

    private void handleJoinRoom(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String room = JsonUtil.getString(body, "room", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();

        if (room.isEmpty() || username.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Room and username are required.");
            return;
        }

        boolean ok = roomService.joinRoom(room, username);
        if (!ok) {
            sendError(exchange, 404, "ROOM_NOT_FOUND", "Room does not exist.");
            return;
        }

        chatService.addSystemMessage(room, username + " joined " + room);
        Logger.info("ROOM", username + " joined " + room);
        sendSuccess(exchange, "{\"joined\":true}");
    }

    private void handleLeaveRoom(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String room = JsonUtil.getString(body, "room", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();

        roomService.leaveRoom(room, username);
        chatService.addSystemMessage(room, username + " left " + room);
        sendSuccess(exchange, "{\"left\":true}");
    }

    private void handleDeleteRoom(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String room = JsonUtil.getString(body, "room", "").trim();
        String requester = JsonUtil.getString(body, "username", "").trim();
        String token = JsonUtil.getString(body, "adminToken", "").trim();

        boolean deleted = roomService.deleteRoom(room, requester, !token.isEmpty());
        if (!deleted) {
            sendError(exchange, 403, "CANNOT_DELETE", "Cannot delete default room or you do not have permission.");
            return;
        }

        chatService.clearRoom(room);
        Logger.info("ROOM", "Deleted " + room + " by " + requester);
        sendSuccess(exchange, "{\"deleted\":true}");
    }
}
