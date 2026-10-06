package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.util.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StaticFileHandler implements HttpHandler {
    private final Path webRoot;

    public StaticFileHandler() {
        this.webRoot = Paths.get("web").toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String pathStr = exchange.getRequestURI().getPath();
        if (pathStr == null || pathStr.equals("/") || pathStr.isEmpty()) {
            pathStr = "/index.html";
        }

        // Prevent path traversal
        pathStr = URLDecoder.decode(pathStr, StandardCharsets.UTF_8);
        Path target = webRoot.resolve(pathStr.startsWith("/") ? pathStr.substring(1) : pathStr).normalize();

        if (!target.startsWith(webRoot) || !Files.exists(target) || Files.isDirectory(target)) {
            byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, notFound.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound);
            }
            exchange.close();
            return;
        }

        String contentType = probeContentType(target.getFileName().toString());
        long length = Files.size(target);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(method) ? -1 : length);

        if ("GET".equalsIgnoreCase(method)) {
            try (InputStream is = Files.newInputStream(target);
                 OutputStream os = exchange.getResponseBody()) {
                is.transferTo(os);
            }
        }
        exchange.close();
    }

    private String probeContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
