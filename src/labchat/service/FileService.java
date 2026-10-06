package labchat.service;

import labchat.config.ServerConfig;
import labchat.model.FileInfo;
import labchat.util.Logger;
import labchat.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FileService {
    private final ServerConfig config;
    private final Path uploadDir;
    private final ConcurrentHashMap<String, FileInfo> filesById = new ConcurrentHashMap<>();

    public FileService(ServerConfig config) {
        this.config = config;
        this.uploadDir = Paths.get("data", "uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            Logger.error("FILE", "Failed to create uploads directory: " + uploadDir, e);
        }
    }

    public FileInfo saveFile(String originalFilename, String contentType, String uploader, InputStream inputStream, long declaredLength) throws IOException {
        String sanitized = ValidationUtil.sanitizeFilename(originalFilename);
        String id = UUID.randomUUID().toString();
        String storedName = id + "_" + sanitized;
        Path targetPath = uploadDir.resolve(storedName).normalize();

        // Enforce path traversal protection
        if (!targetPath.startsWith(uploadDir)) {
            throw new SecurityException("Invalid target file path.");
        }

        long bytesCopied = 0;
        try (OutputStream out = Files.newOutputStream(targetPath)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                bytesCopied += read;
                if (bytesCopied > config.getMaxFileSizeBytes()) {
                    Files.deleteIfExists(targetPath);
                    throw new IOException("File size exceeds maximum allowed limit (" + (config.getMaxFileSizeBytes() / (1024 * 1024)) + " MB).");
                }
                out.write(buffer, 0, read);
            }
        }

        if (contentType == null || contentType.isBlank()) {
            contentType = probeContentType(sanitized);
        }

        FileInfo info = new FileInfo(id, sanitized, storedName, bytesCopied, contentType, uploader);
        filesById.put(id, info);
        Logger.info("FILE", "Uploaded " + sanitized + " (" + bytesCopied + " bytes) by " + uploader);
        return info;
    }

    public FileInfo getFileInfo(String id) {
        return filesById.get(id);
    }

    public Path getFilePath(FileInfo info) {
        if (info == null) return null;
        Path p = uploadDir.resolve(info.getStoredName()).normalize();
        if (!p.startsWith(uploadDir) || !Files.exists(p)) {
            return null;
        }
        return p;
    }

    public int getTotalFilesCount() {
        return filesById.size();
    }

    private String probeContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h")) return "text/x-c";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
