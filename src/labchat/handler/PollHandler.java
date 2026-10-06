package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.model.Poll;
import labchat.service.ChatService;
import labchat.service.PollService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;
import labchat.util.RateLimiter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PollHandler extends BaseHandler implements HttpHandler {
    private final PollService pollService;
    private final UserService userService;
    private final ChatService chatService;
    private final RateLimiter pollRateLimiter;

    public PollHandler(PollService pollService, UserService userService, ChatService chatService) {
        this.pollService = pollService;
        this.userService = userService;
        this.chatService = chatService;
        this.pollRateLimiter = new RateLimiter(5, 60_000); // 5 polls per min
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
                case "/api/polls" -> handleGetPolls(exchange);
                case "/api/poll/create" -> handleCreatePoll(exchange);
                case "/api/poll/vote" -> handleVotePoll(exchange);
                case "/api/poll/close" -> handleClosePoll(exchange);
                default -> sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("POLL", "Error processing " + path, e);
            sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
        }
    }

    private void handleGetPolls(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        List<Poll> list = pollService.getAllPolls();
        StringBuilder json = new StringBuilder("{\"polls\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            json.append(list.get(i).toJson());
        }
        json.append("]}");
        sendSuccess(exchange, json.toString());
    }

    private void handleCreatePoll(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String question = JsonUtil.getString(body, "question", "").trim();
        String creator = JsonUtil.getString(body, "creator", "").trim();
        String room = JsonUtil.getString(body, "room", "#general").trim();
        List<String> options = JsonUtil.getStringList(body, "options");

        if (!pollRateLimiter.allow(creator)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "Too many poll creations. Please wait.");
            return;
        }

        if (question.isEmpty() || options.size() < 2) {
            sendError(exchange, 400, "INVALID_POLL", "A question and at least 2 options are required.");
            return;
        }

        Poll poll = pollService.createPoll(question, options, creator);
        if (poll == null) {
            sendError(exchange, 400, "CREATE_FAILED", "Failed to create poll.");
            return;
        }

        chatService.addSystemMessage(room, "📊 " + creator + " created a new poll: " + question);
        Logger.info("POLL", "Created poll '" + question + "' by " + creator);
        sendSuccess(exchange, poll.toJson());
    }

    private void handleVotePoll(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String pollId = JsonUtil.getString(body, "pollId", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();
        Integer optionIndex = JsonUtil.getInteger(body, "optionIndex", -1);

        if (pollId.isEmpty() || username.isEmpty() || optionIndex < 0) {
            sendError(exchange, 400, "INVALID_VOTE", "Poll ID, username, and valid option index are required.");
            return;
        }

        boolean voted = pollService.vote(pollId, username, optionIndex);
        if (!voted) {
            sendError(exchange, 400, "VOTE_FAILED", "Poll is closed or invalid option.");
            return;
        }

        Poll p = pollService.getPoll(pollId);
        sendSuccess(exchange, p != null ? p.toJson() : "{}");
    }

    private void handleClosePoll(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }
        Map<String, Object> body = JsonUtil.parseObject(readBody(exchange));
        String pollId = JsonUtil.getString(body, "pollId", "").trim();
        String username = JsonUtil.getString(body, "username", "").trim();
        boolean isAdmin = JsonUtil.getBoolean(body, "isAdmin", false);

        boolean closed = pollService.closePoll(pollId, username, isAdmin);
        if (!closed) {
            sendError(exchange, 403, "CLOSE_FAILED", "Only the poll creator or admin can close this poll.");
            return;
        }
        Poll p = pollService.getPoll(pollId);
        sendSuccess(exchange, p != null ? p.toJson() : "{}");
    }
}
