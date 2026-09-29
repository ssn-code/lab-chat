package labchat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** JSON API with minimal parsing tailored to the two string-field request bodies. */
public final class ApiHandler implements HttpHandler {
    private static final int MAX_BODY_BYTES = 2_048;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
    private final ChatState state;

    public ApiHandler(ChatState state) { this.state = state; }

    @Override public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (method.equals("POST") && path.equals("/api/join")) join(exchange);
        else if (method.equals("POST") && path.equals("/api/send")) send(exchange);
        else if (method.equals("POST") && path.equals("/api/ping")) ping(exchange);
        else if (method.equals("GET") && path.equals("/api/messages")) messages(exchange);
        else if (method.equals("GET") && path.equals("/api/users")) users(exchange);
        else sendJson(exchange, 404, error("Unknown endpoint."));
    }

    private void join(HttpExchange ex) throws IOException {
        String username = field(readBody(ex), "username");
        String problem = ChatState.validateUsername(username);
        if (problem != null) { sendJson(ex, 400, error(problem)); return; }
        boolean newUser = state.join(username);
        if (newUser) System.out.println("[JOIN] " + username);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void send(HttpExchange ex) throws IOException {
        String body = readBody(ex);
        String username = field(body, "username");
        String message = field(body, "message");
        String problem = ChatState.validateUsername(username);
        if (problem == null) problem = ChatState.validateMessage(message);
        if (problem != null) { sendJson(ex, 400, error(problem)); return; }
        if (!state.send(username, message)) { sendJson(ex, 409, error("Join the chat before sending a message.")); return; }
        System.out.println("[MSG] " + username + ": " + message);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void ping(HttpExchange ex) throws IOException {
        String username = field(readBody(ex), "username");
        if (ChatState.validateUsername(username) != null || !state.touch(username)) {
            sendJson(ex, 409, error("User session is no longer active.")); return;
        }
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void messages(HttpExchange ex) throws IOException {
        StringBuilder json = new StringBuilder("{\"messages\":[");
        List<ChatMessage> all = state.messages();
        for (int i = 0; i < all.size(); i++) {
            ChatMessage m = all.get(i);
            if (i > 0) json.append(',');
            json.append("{\"username\":\"").append(escape(m.username())).append("\",")
                    .append("\"message\":\"").append(escape(m.message())).append("\",")
                    .append("\"timestamp\":\"").append(escape(TIME.format(m.timestamp()))).append("\",")
                    .append("\"system\":").append(m.system()).append('}');
        }
        sendJson(ex, 200, json.append("]}").toString());
    }

    private void users(HttpExchange ex) throws IOException {
        List<String> names = state.usernames();
        StringBuilder json = new StringBuilder("{\"count\":").append(names.size()).append(",\"users\":[");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(escape(names.get(i))).append('"');
        }
        sendJson(ex, 200, json.append("]}").toString());
    }

    private static String readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Request body is too large.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // Accepts JSON string values and handles the escapes produced by JSON.stringify().
    private static String field(String json, String name) {
        Matcher match = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        return match.find() ? unescape(match.group(1)) : null;
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 >= text.length()) { result.append(c); continue; }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> result.append('\n'); case 'r' -> result.append('\r'); case 't' -> result.append('\t');
                case 'b' -> result.append('\b'); case 'f' -> result.append('\f'); case '"' -> result.append('"');
                case '\\' -> result.append('\\'); case '/' -> result.append('/');
                case 'u' -> { if (i + 4 >= text.length()) return null; try { result.append((char) Integer.parseInt(text.substring(i + 1, i + 5), 16)); i += 4; } catch (NumberFormatException e) { return null; } }
                default -> { return null; }
            }
        }
        return result.toString();
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) { case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t"); default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); } }
        }
        return out.toString();
    }

    static String error(String message) { return "{\"success\":false,\"error\":\"" + escape(message) + "\"}"; }
    static void sendJson(HttpExchange ex, int status, String json) throws IOException { send(ex, status, "application/json; charset=utf-8", json); }
    static void sendText(HttpExchange ex, int status, String text) throws IOException { send(ex, status, "text/plain; charset=utf-8", text); }
    private static void send(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
