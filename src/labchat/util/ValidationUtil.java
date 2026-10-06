package labchat.util;

import java.util.regex.Pattern;

public final class ValidationUtil {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,20}$");
    private static final Pattern ROOM_PATTERN = Pattern.compile("^#[a-zA-Z0-9_-]{1,30}$");

    private ValidationUtil() {}

    public static String validateUsername(String username, int maxLength) {
        if (username == null || username.trim().isEmpty()) {
            return "Username cannot be empty.";
        }
        String clean = username.trim();
        if (clean.length() > maxLength) {
            return "Username must be " + maxLength + " characters or fewer.";
        }
        if (!USERNAME_PATTERN.matcher(clean).matches()) {
            return "Username may only contain letters, numbers, hyphens, dots, and underscores.";
        }
        return null;
    }

    public static String validateRoomName(String room) {
        if (room == null || room.trim().isEmpty()) {
            return "Room name cannot be empty.";
        }
        String clean = room.trim();
        if (!clean.startsWith("#")) {
            clean = "#" + clean;
        }
        if (!ROOM_PATTERN.matcher(clean).matches()) {
            return "Room name must start with '#' and contain only alphanumeric characters, underscores, or hyphens (max 30 chars).";
        }
        return null;
    }

    public static String validateMessage(String message, int maxLength) {
        if (message == null || message.trim().isEmpty()) {
            return "Message cannot be empty.";
        }
        if (message.length() > maxLength) {
            return "Message must be " + maxLength + " characters or fewer.";
        }
        return null;
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed_file";
        }
        // Remove path elements and any risky characters
        String name = filename.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            name = "file_" + System.currentTimeMillis();
        }
        if (name.length() > 80) {
            name = name.substring(0, 80);
        }
        return name;
    }
}
