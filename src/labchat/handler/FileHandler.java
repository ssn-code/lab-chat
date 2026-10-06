package labchat.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import labchat.config.ServerConfig;
import labchat.model.FileInfo;
import labchat.service.FileService;
import labchat.service.UserService;
import labchat.util.JsonUtil;
import labchat.util.Logger;
import labchat.util.RateLimiter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileHandler extends BaseHandler implements HttpHandler {
    private final ServerConfig config;
    private final FileService fileService;
    private final UserService userService;
    private final RateLimiter uploadRateLimiter;

    public FileHandler(ServerConfig config, FileService fileService, UserService userService) {
        this.config = config;
        this.fileService = fileService;
        this.userService = userService;
        this.uploadRateLimiter = new RateLimiter(config.getUploadRateLimitPerMinute(), 60_000);
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
            if ("/api/upload".equals(path)) {
                handleUpload(exchange);
            } else if ("/api/download".equals(path) || path.startsWith("/api/download/")) {
                handleDownload(exchange);
            } else {
                sendError(exchange, 404, "NOT_FOUND", "Endpoint not found");
            }
        } catch (Exception e) {
            Logger.error("FILE", "Error in FileHandler: " + e.getMessage(), e);
            sendError(exchange, 500, "SERVER_ERROR", "File processing error: " + e.getMessage());
        }
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            sendError(exchange, 400, "BAD_REQUEST", "Content-Type header is missing.");
            return;
        }

        String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        if (!uploadRateLimiter.allow(clientIp)) {
            sendError(exchange, 429, "RATE_LIMIT_EXCEEDED", "Upload rate limit exceeded. Please wait.");
            return;
        }

        // Check if multipart/form-data
        if (contentType.toLowerCase().contains("multipart/form-data")) {
            parseMultipartUpload(exchange, contentType);
        } else {
            // Direct octet-stream upload with headers
            String filename = exchange.getRequestHeaders().getFirst("X-File-Name");
            String uploader = exchange.getRequestHeaders().getFirst("X-Uploader");
            if (filename == null || filename.isBlank()) filename = "upload_" + System.currentTimeMillis();
            if (uploader == null || uploader.isBlank()) uploader = "Anonymous";

            FileInfo info = fileService.saveFile(filename, contentType, uploader, exchange.getRequestBody(), -1);
            sendSuccess(exchange, info.toJson());
        }
    }

    private void parseMultipartUpload(HttpExchange exchange, String contentType) throws IOException {
        String boundary = null;
        for (String param : contentType.split(";")) {
            String p = param.trim();
            if (p.toLowerCase().startsWith("boundary=")) {
                boundary = p.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
            }
        }

        if (boundary == null) {
            sendError(exchange, 400, "BAD_BOUNDARY", "Multipart boundary is missing.");
            return;
        }

        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        InputStream is = exchange.getRequestBody();

        // Read all stream bytes (up to max file size + 1MB overhead)
        long maxBytes = config.getMaxFileSizeBytes() + 1024 * 1024;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        long total = 0;
        while ((read = is.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                sendError(exchange, 413, "PAYLOAD_TOO_LARGE", "Uploaded file exceeds maximum limit.");
                return;
            }
            buffer.write(chunk, 0, read);
        }

        byte[] payload = buffer.toByteArray();
        ParsedPart filePart = null;
        String uploader = "Anonymous";

        int pos = 0;
        while (pos < payload.length) {
            int partStart = indexOf(payload, boundaryBytes, pos);
            if (partStart == -1) break;
            partStart += boundaryBytes.length;

            // Check if end of multipart "--"
            if (partStart + 1 < payload.length && payload[partStart] == '-' && payload[partStart + 1] == '-') {
                break;
            }
            // Skip CRLF
            if (partStart + 1 < payload.length && payload[partStart] == '\r' && payload[partStart + 1] == '\n') {
                partStart += 2;
            }

            int nextBoundary = indexOf(payload, boundaryBytes, partStart);
            int partEnd = (nextBoundary != -1) ? nextBoundary : payload.length;

            // Search headers in this part: CRLF CRLF
            byte[] headerEndMarker = new byte[]{'\r', '\n', '\r', '\n'};
            int headerEnd = indexOf(payload, headerEndMarker, partStart);
            if (headerEnd == -1 || headerEnd >= partEnd) {
                pos = partEnd;
                continue;
            }

            String headersStr = new String(payload, partStart, headerEnd - partStart, StandardCharsets.UTF_8);
            int dataStart = headerEnd + 4;
            int dataEnd = partEnd;
            if (dataEnd >= 2 && payload[dataEnd - 2] == '\r' && payload[dataEnd - 1] == '\n') {
                dataEnd -= 2; // trim trailing CRLF before boundary
            }

            if (headersStr.contains("name=\"uploader\"")) {
                uploader = new String(payload, dataStart, Math.max(0, dataEnd - dataStart), StandardCharsets.UTF_8).trim();
            } else if (headersStr.contains("name=\"file\"") || headersStr.contains("filename=")) {
                String filename = extractFilename(headersStr);
                String partContentType = extractContentType(headersStr);
                byte[] fileBytes = new byte[Math.max(0, dataEnd - dataStart)];
                System.arraycopy(payload, dataStart, fileBytes, 0, fileBytes.length);
                filePart = new ParsedPart(filename, partContentType, fileBytes);
            }

            pos = partEnd;
        }

        if (filePart == null || filePart.bytes.length == 0) {
            sendError(exchange, 400, "NO_FILE", "No file found in upload request.");
            return;
        }

        FileInfo info = fileService.saveFile(
                filePart.filename,
                filePart.contentType,
                uploader,
                new java.io.ByteArrayInputStream(filePart.bytes),
                filePart.bytes.length
        );

        sendSuccess(exchange, info.toJson());
    }

    private void handleDownload(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "METHOD_NOT_ALLOWED", "Method not allowed");
            return;
        }

        String fileId = null;
        String path = exchange.getRequestURI().getPath();
        if (path.startsWith("/api/download/")) {
            fileId = path.substring("/api/download/".length()).trim();
        } else {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String p : query.split("&")) {
                    int eq = p.indexOf('=');
                    if (eq > 0 && "id".equals(p.substring(0, eq))) {
                        fileId = java.net.URLDecoder.decode(p.substring(eq + 1), StandardCharsets.UTF_8);
                    }
                }
            }
        }

        if (fileId == null || fileId.isBlank()) {
            sendError(exchange, 400, "INVALID_ID", "File ID is required.");
            return;
        }

        FileInfo info = fileService.getFileInfo(fileId);
        if (info == null) {
            sendError(exchange, 404, "NOT_FOUND", "File not found.");
            return;
        }

        Path filePath = fileService.getFilePath(info);
        if (filePath == null || !Files.exists(filePath)) {
            sendError(exchange, 404, "NOT_FOUND", "File does not exist on disk.");
            return;
        }

        String disposition = info.isImage() ? "inline" : "attachment; filename=\"" + info.getOriginalName().replace("\"", "_") + "\"";
        exchange.getResponseHeaders().set("Content-Type", info.getContentType());
        exchange.getResponseHeaders().set("Content-Disposition", disposition);
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(info.getSizeBytes()));
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        exchange.sendResponseHeaders(200, "HEAD".equalsIgnoreCase(method) ? -1 : info.getSizeBytes());

        if ("GET".equalsIgnoreCase(method)) {
            try (InputStream is = Files.newInputStream(filePath);
                 OutputStream os = exchange.getResponseBody()) {
                is.transferTo(os);
            }
        }
        exchange.close();
    }

    private static int indexOf(byte[] data, byte[] target, int start) {
        if (target.length == 0) return start;
        for (int i = start; i <= data.length - target.length; i++) {
            boolean found = true;
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }

    private static String extractFilename(String headers) {
        int idx = headers.indexOf("filename=\"");
        if (idx != -1) {
            int start = idx + 10;
            int end = headers.indexOf('"', start);
            if (end != -1) return headers.substring(start, end);
        }
        return "uploaded_file";
    }

    private static String extractContentType(String headers) {
        int idx = headers.toLowerCase().indexOf("content-type:");
        if (idx != -1) {
            int start = idx + 13;
            int end = headers.indexOf("\r\n", start);
            if (end != -1) return headers.substring(start, end).trim();
            return headers.substring(start).trim();
        }
        return "application/octet-stream";
    }

    private static class ParsedPart {
        final String filename;
        final String contentType;
        final byte[] bytes;

        ParsedPart(String filename, String contentType, byte[] bytes) {
            this.filename = filename;
            this.contentType = contentType;
            this.bytes = bytes;
        }
    }
}
