package labchat.model;

import labchat.util.JsonUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ChatMessage {
    private final String id;
    private final String username;
    private volatile String message;
    private final Instant timestamp;
    private final boolean system;
    private final String room;
    private final String recipient; // empty string if public/room message
    private volatile String replyToId; // null or id of replied message
    private volatile String replyToSnippet; // snippet of replied message
    private volatile String replyToUser; // author of replied message
    private volatile boolean edited;
    private volatile boolean deleted;
    private volatile boolean pinned;
    private volatile String codeLanguage; // e.g. "java", "python" or null
    private volatile FileInfo fileInfo; // attached file or null
    private final Reaction reaction = new Reaction();

    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());

    public ChatMessage(String id, String username, String message, Instant timestamp, boolean system, String room, String recipient) {
        this.id = id;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.system = system;
        this.room = room != null ? room : "#general";
        this.recipient = recipient != null ? recipient : "";
        this.edited = false;
        this.deleted = false;
        this.pinned = false;
        this.codeLanguage = null;
        this.fileInfo = null;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isSystem() { return system; }
    public String getRoom() { return room; }
    public String getRecipient() { return recipient; }
    public boolean isPrivate() { return recipient != null && !recipient.isBlank(); }
    public boolean isEdited() { return edited; }
    public boolean isDeleted() { return deleted; }
    public boolean isPinned() { return pinned; }
    public String getCodeLanguage() { return codeLanguage; }
    public FileInfo getFileInfo() { return fileInfo; }
    public Reaction getReaction() { return reaction; }
    public String getReplyToId() { return replyToId; }
    public String getReplyToSnippet() { return replyToSnippet; }
    public String getReplyToUser() { return replyToUser; }

    public void setReplyTo(String id, String user, String snippet) {
        this.replyToId = id;
        this.replyToUser = user;
        this.replyToSnippet = snippet;
    }

    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean edit(String newMessage) {
        if (deleted) return false;
        this.message = newMessage;
        this.edited = true;
        return true;
    }

    public boolean markDeleted() {
        if (deleted) return false;
        this.deleted = true;
        this.message = "This message was deleted.";
        return true;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(JsonUtil.escape(id)).append("\",");
        sb.append("\"username\":\"").append(JsonUtil.escape(username)).append("\",");
        sb.append("\"message\":\"").append(JsonUtil.escape(message)).append("\",");
        sb.append("\"timestamp\":\"").append(JsonUtil.escape(ISO_TIME.format(timestamp))).append("\",");
        sb.append("\"system\":").append(system).append(",");
        sb.append("\"room\":\"").append(JsonUtil.escape(room)).append("\",");
        sb.append("\"recipient\":\"").append(JsonUtil.escape(recipient)).append("\",");
        sb.append("\"edited\":").append(edited).append(",");
        sb.append("\"deleted\":").append(deleted).append(",");
        sb.append("\"pinned\":").append(pinned).append(",");
        if (codeLanguage != null) {
            sb.append("\"codeLanguage\":\"").append(JsonUtil.escape(codeLanguage)).append("\",");
        } else {
            sb.append("\"codeLanguage\":null,");
        }
        if (replyToId != null) {
            sb.append("\"replyTo\":{")
              .append("\"id\":\"").append(JsonUtil.escape(replyToId)).append("\",")
              .append("\"user\":\"").append(JsonUtil.escape(replyToUser)).append("\",")
              .append("\"snippet\":\"").append(JsonUtil.escape(replyToSnippet)).append("\"")
              .append("},");
        } else {
            sb.append("\"replyTo\":null,");
        }
        if (fileInfo != null) {
            sb.append("\"file\":").append(fileInfo.toJson()).append(",");
        } else {
            sb.append("\"file\":null,");
        }
        sb.append("\"reactions\":").append(reaction.toJson());
        sb.append("}");
        return sb.toString();
    }
}
