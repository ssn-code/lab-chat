package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public final class ApiHandler extends BaseHandler implements HttpHandler {
    private final UserHandler userHandler;
    private final MessageHandler messageHandler;
    private final RoomHandler roomHandler;
    private final FileHandler fileHandler;
    private final PollHandler pollHandler;
    private final AdminHandler adminHandler;

    public ApiHandler(UserHandler userHandler, MessageHandler messageHandler, RoomHandler roomHandler,
                      FileHandler fileHandler, PollHandler pollHandler, AdminHandler adminHandler) {
        this.userHandler = userHandler;
        this.messageHandler = messageHandler;
        this.roomHandler = roomHandler;
        this.fileHandler = fileHandler;
        this.pollHandler = pollHandler;
        this.adminHandler = adminHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/join") || path.startsWith("/api/guest") || path.startsWith("/api/leave") ||
            path.startsWith("/api/users") || path.startsWith("/api/presence")) {
            userHandler.handle(exchange);
        } else if (path.startsWith("/api/rooms")) {
            roomHandler.handle(exchange);
        } else if (path.startsWith("/api/upload") || path.startsWith("/api/download")) {
            fileHandler.handle(exchange);
        } else if (path.startsWith("/api/poll")) {
            pollHandler.handle(exchange);
        } else if (path.startsWith("/api/admin") || path.startsWith("/api/server/status")) {
            adminHandler.handle(exchange);
        } else if (path.startsWith("/api/messages") || path.startsWith("/api/send") ||
                   path.startsWith("/api/private") || path.startsWith("/api/message") ||
                   path.startsWith("/api/pinned") || path.startsWith("/api/typing")) {
            messageHandler.handle(exchange);
        } else {
            sendError(exchange, 404, "NOT_FOUND", "Unknown API endpoint: " + path);
        }
    }
}
