package labchat.model;

import labchat.util.JsonUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class User {
    private final String username;
    private volatile Instant lastSeen;
    private volatile String status; // "online", "idle", "away", "offline"
    private volatile String currentRoom;
    private volatile boolean muted;
    private volatile Instant banUntil;
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public User(String username) {
        this.username = username;
        this.lastSeen = Instant.now();
        this.status = "online";
        this.currentRoom = "#general";
        this.muted = false;
        this.banUntil = null;
    }

    public String getUsername() { return username; }
    public Instant getLastSeen() { return lastSeen; }
    public String getStatus() { return status; }
    public String getCurrentRoom() { return currentRoom; }
    public boolean isMuted() { return muted; }
    public Instant getBanUntil() { return banUntil; }

    public void touch() {
        this.lastSeen = Instant.now();
        if ("offline".equals(this.status)) {
            this.status = "online";
        }
    }

    public void setStatus(String status) {
        this.status = (status != null && !status.isBlank()) ? status : "online";
        this.lastSeen = Instant.now();
    }

    public void setCurrentRoom(String room) {
        this.currentRoom = room;
    }

    public void setMuted(boolean muted) { this.muted = muted; }

    public void setBanMinutes(int minutes) {
        if (minutes <= 0) {
            this.banUntil = null;
        } else {
            this.banUntil = Instant.now().plusSeconds(minutes * 60L);
        }
    }

    public boolean isBanned() {
        if (banUntil == null) return false;
        if (Instant.now().isAfter(banUntil)) {
            banUntil = null;
            return false;
        }
        return true;
    }

    public String toJson() {
        return "{" +
                "\"username\":\"" + JsonUtil.escape(username) + "\"," +
                "\"status\":\"" + JsonUtil.escape(status) + "\"," +
                "\"currentRoom\":\"" + JsonUtil.escape(currentRoom) + "\"," +
                "\"lastSeen\":\"" + JsonUtil.escape(ISO_TIME.format(lastSeen)) + "\"," +
                "\"muted\":" + muted + "," +
                "\"banned\":" + isBanned() +
                "}";
    }
}
