package labchat.service;

import labchat.config.ServerConfig;
import labchat.model.ChatMessage;
import labchat.model.User;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class AdminService {
    private final ServerConfig config;
    private final UserService userService;
    private final RoomService roomService;
    private final ChatService chatService;
    private final FileService fileService;
    private final Instant startTime;
    private volatile String currentAnnouncement = null;
    private volatile Instant announcementTime = null;

    public AdminService(ServerConfig config, UserService userService, RoomService roomService, ChatService chatService, FileService fileService) {
        this.config = config;
        this.userService = userService;
        this.roomService = roomService;
        this.chatService = chatService;
        this.fileService = fileService;
        this.startTime = Instant.now();
    }

    public boolean authenticate(String token) {
        if (token == null) return false;
        return config.getAdminToken().equals(token.trim());
    }

    public boolean kickUser(String username) {
        return userService.leave(username);
    }

    public boolean muteUser(String username, boolean muted) {
        User u = userService.getUser(username);
        if (u == null) return false;
        u.setMuted(muted);
        return true;
    }

    public boolean banUser(String username, int minutes) {
        User u = userService.getUser(username);
        if (u == null) return false;
        u.setBanMinutes(minutes);
        return true;
    }

    public void postAnnouncement(String text) {
        this.currentAnnouncement = text;
        this.announcementTime = Instant.now();
        // Also broadcast to #general and system
        chatService.addSystemMessage("#general", "📢 LAB ANNOUNCEMENT: " + text);
    }

    public void clearAnnouncement() {
        this.currentAnnouncement = null;
        this.announcementTime = null;
    }

    public String getCurrentAnnouncement() {
        return currentAnnouncement;
    }

    public String getStatusJson() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long hours = uptime.toHours();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        String uptimeStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        long usedBytes = mem.getHeapMemoryUsage().getUsed();
        long maxBytes = mem.getHeapMemoryUsage().getMax();
        String memoryStr = (usedBytes / (1024 * 1024)) + " MB / " + (maxBytes / (1024 * 1024)) + " MB";

        return "{" +
                "\"host\":\"" + config.getHost() + "\"," +
                "\"port\":" + config.getPort() + "," +
                "\"uptime\":\"" + uptimeStr + "\"," +
                "\"activeUsers\":" + userService.getActiveUserCount() + "," +
                "\"activeRooms\":" + roomService.getAllRooms().size() + "," +
                "\"totalMessages\":" + chatService.getTotalMessageCount() + "," +
                "\"totalFiles\":" + fileService.getTotalFilesCount() + "," +
                "\"memoryUsage\":\"" + memoryStr + "\"," +
                "\"announcement\":" + (currentAnnouncement == null ? "null" : "\"" + labchat.util.JsonUtil.escape(currentAnnouncement) + "\"") +
                "}";
    }
}
