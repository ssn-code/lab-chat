package labchat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves only the three known web assets; it never maps arbitrary paths to disk. */
public final class StaticFileHandler implements HttpHandler {
    private final Path webDirectory;

    public StaticFileHandler(Path webDirectory) { this.webDirectory = webDirectory; }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            ApiHandler.sendText(exchange, 405, "Method not allowed"); return;
        }
        String requestPath = exchange.getRequestURI().getPath();
        String file = switch (requestPath) {
            case "/" -> "index.html";
            case "/style.css" -> "style.css";
            case "/app.js" -> "app.js";
            default -> null;
        };
        if (file == null) { ApiHandler.sendText(exchange, 404, "Not found"); return; }
        Path asset = webDirectory.resolve(file);
        if (!Files.isRegularFile(asset)) { ApiHandler.sendText(exchange, 404, "Asset not found"); return; }
        byte[] bytes = Files.readAllBytes(asset);
        String type = file.endsWith(".html") ? "text/html; charset=utf-8"
                : file.endsWith(".css") ? "text/css; charset=utf-8"
                : "application/javascript; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
