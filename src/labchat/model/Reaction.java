package labchat.model;

import labchat.util.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Reaction {
    // emoji -> list of usernames who reacted
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> reactions = new ConcurrentHashMap<>();

    public boolean toggle(String emoji, String username) {
        if (emoji == null || username == null) return false;
        ConcurrentHashMap<String, Boolean> users = reactions.computeIfAbsent(emoji, k -> new ConcurrentHashMap<>());
        if (users.containsKey(username)) {
            users.remove(username);
            if (users.isEmpty()) {
                reactions.remove(emoji);
            }
            return false; // removed
        } else {
            users.put(username, Boolean.TRUE);
            return true; // added
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : reactions.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(JsonUtil.escape(entry.getKey())).append("\":{");
            sb.append("\"count\":").append(entry.getValue().size()).append(",");
            sb.append("\"users\":[");
            boolean uFirst = true;
            for (String u : entry.getValue().keySet()) {
                if (!uFirst) sb.append(",");
                uFirst = false;
                sb.append("\"").append(JsonUtil.escape(u)).append("\"");
            }
            sb.append("]}");
        }
        sb.append("}");
        return sb.toString();
    }
}
