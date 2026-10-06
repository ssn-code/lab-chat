package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.config.ServerConfig;
import labchat.model.ChatMessage;
import labchat.model.FileInfo;
import labchat.service.ChatService;
import labchat.service.FileService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;
import labchat.util.RateLimiter;
import labchat.util.ValidationUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public final class MessageHandler extends BaseHandler implements HttpHandler {
    private final ServerConfig config;
    private final ChatService chatService;
    private final UserService userService;
    private final FileService fileService;
    private final RateLimiter messageRateLimiter;

    public MessageHandler(ServerConfig config, ChatService chatService, UserService userService, FileService fileService) {
        this.config = config;
        this.chatService = chatService;
        this.userService = userService;
        this.fileService = fileService;
        this.messageRateLimiter = new RateLimiter(config.getMessageRateLimitPerSecond(), 1000);
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
                case "/api/messages" -> handleGetRoomMessages(exchange);
                case "/api/send" -> handleSendRoomMessage(exchange);
                case "/api/private/messages" -> handleGetPrivateMessages(exchange);
                case "/api/private/send" -> handleSendPrivateMessage(exchange);
                case "/api/message/edit" -> handleEditMessage(exchange);
                case "/api/message/delete" -> handleDeleteMessage(exchange);
                case "/api/message/react" -> handleReactMessage(exchange);
                case "/api/message/pin" -> handlePinMessage(exchange);
                case "/api/pinned" -> handleGetPinnedMessages(exchange);
                case "/api/typing" -> handleTyping(exchange);
                default -> sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("MSG", "Error processing " + path, e);
            sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
        }
    }

    private void handleGetRoomMessages(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String room = "#general";
        String currentUser = "";
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq);
                    String v = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                    if ("room".equals(k)) room = v;
                    if ("username".equals(k)) currentUser = v;
                }
            }
        }

        List<ChatMessage> list = chatService.getRoomMessages(room);
        List<String> typing = chatService.getTypingUsers(room, currentUser);

        StringBuilder json = new StringBuilder("{\"room\":\"").append(JsonUtil.escape(room)).append("\",");
        json.append("\"typing\":[");
        for (int i = 0; i < typing.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(JsonUtil.escape(typing.get(i))).append("\"");
        }
        json.append("],\"messages\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(list.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handleSendRoomMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String username = JsonUtil.getString(body, "username", "").trim();
        String room = JsonUtil.getString(body, "room", "#general").trim();
        String text = JsonUtil.getString(body, "message", "").trim();
        String codeLanguage = JsonUtil.getString(body, "codeLanguage", "").trim();
        String fileId = JsonUtil.getString(body, "fileId", "").trim();
        String replyToId = JsonUtil.getString(body, "replyToId", "").trim();

        if (!messageRateLimiter.allow(username)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "You are sending messages too quickly.");
            return;
        }

        FileInfo fileInfo = null;
        if (!fileId.isEmpty()) {
            fileInfo = fileService.getFileInfo(fileId);
        }

        if (text.isEmpty() && fileInfo == null) {
            sendError(exchange, 400, "EMPTY_MESSAGE", "Message or attached file cannot be empty.");
            return;
        }

        if (text.length() > config.getMaxMessageLength()) {
            sendError(exchange, 400, "MESSAGE_TOO_LONG", "Message exceeds maximum length of " + config.getMaxMessageLength());
            return;
        }

        ChatMessage msg = chatService.sendRoomMessage(username, room, text, codeLanguage, fileInfo, replyToId);
        if (msg == null) {
            sendError(exchange, 403, "SEND_FAILED", "User session is invalid, muted, or banned.");
            return;
        }

        Logger.info("MSG", username + " -> " + room + ": " + (text.length() > 40 ? text.substring(0, 40) + "..." : text));
        sendSuccess(exchange, msg.toJson());
    }

    private void handleGetPrivateMessages(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String u1 = "";
        String u2 = "";
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq);
                    String v = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                    if ("u1".equals(k) || "username".equals(k)) u1 = v;
                    if ("u2".equals(k) || "target".equals(k)) u2 = v;
                }
            }
        }

        if (u1.isEmpty() || u2.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Both users must be specified for private messages.");
            return;
        }

        List<ChatMessage> list = chatService.getPrivateMessages(u1, u2);
        String dmKey = ChatService.getPrivateConversationKey(u1, u2);
        List<String> typing = chatService.getTypingUsers(dmKey, u1);

        StringBuilder json = new StringBuilder("{\"conversation\":\"").append(JsonUtil.escape(dmKey)).append("\",");
        json.append("\"typing\":[");
        for (int i = 0; i < typing.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(JsonUtil.escape(typing.get(i))).append("\"");
        }
        json.append("],\"messages\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(list.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handleSendPrivateMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String sender = JsonUtil.getString(body, "username", "").trim();
        String recipient = JsonUtil.getString(body, "recipient", "").trim();
        String text = JsonUtil.getString(body, "message", "").trim();
        String codeLanguage = JsonUtil.getString(body, "codeLanguage", "").trim();
        String fileId = JsonUtil.getString(body, "fileId", "").trim();
        String replyToId = JsonUtil.getString(body, "replyToId", "").trim();

        if (recipient.isEmpty() || sender.equalsIgnoreCase(recipient)) {
            sendError(exchange, 400, "INVALID_RECIPIENT", "Valid recipient is required.");
            return;
        }

        if (!messageRateLimiter.allow(sender)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "You are sending messages too quickly.");
            return;
        }

        FileInfo fileInfo = null;
        if (!fileId.isEmpty()) {
            fileInfo = fileService.getFileInfo(fileId);
        }

        if (text.isEmpty() && fileInfo == null) {
            sendError(exchange, 400, "EMPTY_MESSAGE", "Message or attached file cannot be empty.");
            return;
        }

        ChatMessage msg = chatService.sendPrivateMessage(sender, recipient, text, codeLanguage, fileInfo, replyToId);
        if (msg == null) {
            sendError(exchange, 403, "SEND_FAILED", "Sender session is invalid, muted, or banned.");
            return;
        }

        Logger.info("DM", sender + " -> " + recipient + ": " + (text.length() > 30 ? text.substring(0, 30) + "..." : text));
        sendSuccess(exchange, msg.toJson());
    }

    private void handleEditMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String messageId = JsonUtil.getString(body, "messageId", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();
        String newMessage = JsonUtil.getString(body, "message", "").trim();

        if (newMessage.isEmpty() || newMessage.length() > config.getMaxMessageLength()) {
            sendError(exchange, 400, "INVALID_MESSAGE", "Message cannot be empty and must be within length limits.");
            return;
        }

        boolean ok = chatService.editMessage(messageId, username, newMessage);
        if (!ok) {
            sendError(exchange, 403, "EDIT_FAILED", "You can only edit your own active messages.");
            return;
        }
        sendSuccess(exchange, "{\"edited\":true}");
    }

    private void handleDeleteMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String messageId = JsonUtil.getString(body, "messageId", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();
        boolean isAdmin = JsonUtil.getBoolean(body, "isAdmin", false);

        boolean ok = chatService.deleteMessage(messageId, username, isAdmin);
        if (!ok) {
            sendError(exchange, 403, "DELETE_FAILED", "Cannot delete message or permission denied.");
            return;
        }
        sendSuccess(exchange, "{\"deleted\":true}");
    }

    private void handleReactMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String messageId = JsonUtil.getString(body, "messageId", "").trim();
        String emoji = JsonUtil.getString(body, "emoji", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();

        boolean toggled = chatService.toggleReaction(messageId, emoji, username);
        ChatMessage msg = chatService.getMessage(messageId);
        String reactionJson = (msg != null) ? msg.getReaction().toJson() : "{}";
        sendSuccess(exchange, "{\"toggled\":" + toggled + ",\"reactions\":" + reactionJson + "}");
    }

    private void handlePinMessage(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String messageId = JsonUtil.getString(body, "messageId", "").trim();
        boolean isAdmin = JsonUtil.getBoolean(body, "isAdmin", false);

        boolean ok = chatService.togglePin(messageId, isAdmin);
        if (!ok) {
            sendError(exchange, 403, "PIN_FAILED", "Only administrators can pin messages.");
            return;
        }
        sendSuccess(exchange, "{\"success\":true}");
    }

    private void handleGetPinnedMessages(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String room = "#general";
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "room".equals(pair.substring(0, eq))) {
                    room = java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        List<ChatMessage> pinned = chatService.getPinnedMessages(room);
        StringBuilder json = new StringBuilder("{\"pinned\":[");
        for (int i = 0; i < pinned.size(); i++) {
            if (i > 0) json.append(",");
            json.append(pinned.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handleTyping(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String username = JsonUtil.getString(body, "username", "").trim();
        String context = JsonUtil.getString(body, "context", "#general").trim();
        String targetUser = JsonUtil.getString(body, "targetUser", "").trim();

        if (username.isEmpty()) {
            sendError(exchange, 400, "INVALID_REQUEST", "Username is required.");
            return;
        }

        String key = context;
        if (!targetUser.isEmpty()) {
            key = ChatService.getPrivateConversationKey(username, targetUser);
        }
        chatService.setTyping(key, username);
        sendSuccess(exchange, "{\"typing\":true}");
    }
}
