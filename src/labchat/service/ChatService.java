package labchat.service;

import labchat.config.ServerConfig;
import labchat.model.ChatMessage;
import labchat.model.FileInfo;
import labchat.model.User;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ChatService {
    private final ServerConfig config;
    private final UserService userService;
    // Room name -> list of messages
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>> roomMessages = new ConcurrentHashMap<>();
    // DM conversation key (sorted username1:username2) -> list of messages
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ChatMessage>> privateMessages = new ConcurrentHashMap<>();
    // All messages map by ID for fast lookup (reactions, edits, deletes, replies, pins)
    private final ConcurrentHashMap<String, ChatMessage> messagesById = new ConcurrentHashMap<>();
    // Typing indicators: context (room or dm key) -> (username -> expireInstant)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> typingMap = new ConcurrentHashMap<>();

    public ChatService(ServerConfig config, UserService userService) {
        this.config = config;
        this.userService = userService;
    }

    public static String getPrivateConversationKey(String u1, String u2) {
        if (u1.compareToIgnoreCase(u2) < 0) {
            return u1.toLowerCase() + ":" + u2.toLowerCase();
        } else {
            return u2.toLowerCase() + ":" + u1.toLowerCase();
        }
    }

    public ChatMessage sendRoomMessage(String username, String room, String text, String codeLanguage, FileInfo fileInfo, String replyToId) {
        User user = userService.getUser(username);
        if (user == null || user.isMuted() || user.isBanned()) {
            return null;
        }
        userService.touch(username);

        String id = UUID.randomUUID().toString();
        ChatMessage msg = new ChatMessage(id, username, text, Instant.now(), false, room, "");
        if (codeLanguage != null && !codeLanguage.isBlank()) {
            msg.setCodeLanguage(codeLanguage.trim().toLowerCase());
        }
        if (fileInfo != null) {
            msg.setFileInfo(fileInfo);
        }
        if (replyToId != null && !replyToId.isBlank()) {
            ChatMessage replied = messagesById.get(replyToId);
            if (replied != null) {
                String snippet = replied.getMessage();
                if (snippet.length() > 60) snippet = snippet.substring(0, 60) + "...";
                msg.setReplyTo(replied.getId(), replied.getUsername(), snippet);
            }
        }

        messagesById.put(id, msg);
        CopyOnWriteArrayList<ChatMessage> list = roomMessages.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>());
        list.add(msg);
        trimHistory(list);
        clearTyping(room, username);
        return msg;
    }

    public ChatMessage sendPrivateMessage(String sender, String recipient, String text, String codeLanguage, FileInfo fileInfo, String replyToId) {
        User user = userService.getUser(sender);
        if (user == null || user.isMuted() || user.isBanned()) {
            return null;
        }
        userService.touch(sender);

        String id = UUID.randomUUID().toString();
        ChatMessage msg = new ChatMessage(id, sender, text, Instant.now(), false, "DM", recipient);
        if (codeLanguage != null && !codeLanguage.isBlank()) {
            msg.setCodeLanguage(codeLanguage.trim().toLowerCase());
        }
        if (fileInfo != null) {
            msg.setFileInfo(fileInfo);
        }
        if (replyToId != null && !replyToId.isBlank()) {
            ChatMessage replied = messagesById.get(replyToId);
            if (replied != null) {
                String snippet = replied.getMessage();
                if (snippet.length() > 60) snippet = snippet.substring(0, 60) + "...";
                msg.setReplyTo(replied.getId(), replied.getUsername(), snippet);
            }
        }

        messagesById.put(id, msg);
        String dmKey = getPrivateConversationKey(sender, recipient);
        CopyOnWriteArrayList<ChatMessage> list = privateMessages.computeIfAbsent(dmKey, k -> new CopyOnWriteArrayList<>());
        list.add(msg);
        trimHistory(list);
        clearTyping(dmKey, sender);
        return msg;
    }

    public void addSystemMessage(String room, String text) {
        String id = UUID.randomUUID().toString();
        ChatMessage msg = new ChatMessage(id, "", text, Instant.now(), true, room, "");
        messagesById.put(id, msg);
        CopyOnWriteArrayList<ChatMessage> list = roomMessages.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>());
        list.add(msg);
        trimHistory(list);
    }

    public List<ChatMessage> getRoomMessages(String room) {
        CopyOnWriteArrayList<ChatMessage> list = roomMessages.get(room);
        if (list == null) return Collections.emptyList();
        return List.copyOf(list);
    }

    public List<ChatMessage> getPrivateMessages(String u1, String u2) {
        String key = getPrivateConversationKey(u1, u2);
        CopyOnWriteArrayList<ChatMessage> list = privateMessages.get(key);
        if (list == null) return Collections.emptyList();
        return List.copyOf(list);
    }

    public ChatMessage getMessage(String id) {
        return messagesById.get(id);
    }

    public boolean editMessage(String messageId, String username, String newText) {
        ChatMessage msg = messagesById.get(messageId);
        if (msg == null || msg.isSystem() || msg.isDeleted()) return false;
        if (!msg.getUsername().equals(username)) return false;
        return msg.edit(newText);
    }

    public boolean deleteMessage(String messageId, String username, boolean isAdmin) {
        ChatMessage msg = messagesById.get(messageId);
        if (msg == null || msg.isDeleted()) return false;
        if (!isAdmin && !msg.getUsername().equals(username)) return false;
        return msg.markDeleted();
    }

    public boolean toggleReaction(String messageId, String emoji, String username) {
        ChatMessage msg = messagesById.get(messageId);
        if (msg == null || msg.isDeleted()) return false;
        return msg.getReaction().toggle(emoji, username);
    }

    public boolean togglePin(String messageId, boolean isAdmin) {
        if (!isAdmin) return false;
        ChatMessage msg = messagesById.get(messageId);
        if (msg == null || msg.isDeleted()) return false;
        msg.setPinned(!msg.isPinned());
        return true;
    }

    public List<ChatMessage> getPinnedMessages(String room) {
        List<ChatMessage> result = new ArrayList<>();
        CopyOnWriteArrayList<ChatMessage> list = roomMessages.get(room);
        if (list != null) {
            for (ChatMessage m : list) {
                if (m.isPinned() && !m.isDeleted()) {
                    result.add(m);
                }
            }
        }
        return result;
    }

    public void clearRoom(String room) {
        CopyOnWriteArrayList<ChatMessage> list = roomMessages.get(room);
        if (list != null) {
            for (ChatMessage m : list) {
                messagesById.remove(m.getId());
            }
            list.clear();
        }
    }

    public void setTyping(String context, String username) {
        ConcurrentHashMap<String, Instant> map = typingMap.computeIfAbsent(context, k -> new ConcurrentHashMap<>());
        map.put(username, Instant.now().plusSeconds(4)); // typing active for 4 seconds
    }

    public void clearTyping(String context, String username) {
        ConcurrentHashMap<String, Instant> map = typingMap.get(context);
        if (map != null) {
            map.remove(username);
        }
    }

    public List<String> getTypingUsers(String context, String excludeUser) {
        ConcurrentHashMap<String, Instant> map = typingMap.get(context);
        if (map == null || map.isEmpty()) return Collections.emptyList();
        Instant now = Instant.now();
        List<String> list = new ArrayList<>();
        map.forEach((user, expiry) -> {
            if (expiry.isAfter(now)) {
                if (!user.equalsIgnoreCase(excludeUser)) {
                    list.add(user);
                }
            } else {
                map.remove(user, expiry);
            }
        });
        return list;
    }

    public int getTotalMessageCount() {
        return messagesById.size();
    }

    private void trimHistory(CopyOnWriteArrayList<ChatMessage> list) {
        while (list.size() > config.getHistorySizePerRoom()) {
            ChatMessage removed = list.remove(0);
            if (removed != null) {
                messagesById.remove(removed.getId());
            }
        }
    }
}
