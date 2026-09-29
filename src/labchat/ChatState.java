package labchat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe, deliberately small in-memory state for the server. */
public final class ChatState {
    public static final int MAX_USERNAME_LENGTH = 20;
    public static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_STORED_MESSAGES = 200;
    private static final Duration INACTIVE_AFTER = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ChatMessage> messages = new CopyOnWriteArrayList<>();

    public boolean join(String username) {
        User prior = users.putIfAbsent(username, new User(username));
        if (prior == null) {
            addSystemMessage(username + " joined the chat");
            return true;
        }
        prior.touch();
        return false;
    }

    public boolean touch(String username) {
        User user = users.get(username);
        if (user == null) return false;
        user.touch();
        return true;
    }

    public boolean send(String username, String message) {
        if (!touch(username)) return false;
        addMessage(new ChatMessage(username, message, Instant.now(), false));
        return true;
    }

    public List<ChatMessage> messages() { return List.copyOf(messages); }

    public List<String> usernames() {
        return users.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** Removes idle users and returns their names so the server can log departures. */
    public List<String> removeInactiveUsers() {
        Instant cutoff = Instant.now().minus(INACTIVE_AFTER);
        List<String> removed = new ArrayList<>();
        users.forEach((name, user) -> {
            if (user.lastSeen().isBefore(cutoff) && users.remove(name, user)) {
                removed.add(name);
                addSystemMessage(name + " left the chat");
            }
        });
        removed.sort(Comparator.naturalOrder());
        return removed;
    }

    private void addSystemMessage(String text) {
        addMessage(new ChatMessage("", text, Instant.now(), true));
    }

    private void addMessage(ChatMessage message) {
        messages.add(message);
        while (messages.size() > MAX_STORED_MESSAGES) messages.remove(0);
    }

    public static String validateUsername(String username) {
        if (username == null) return "Username is required.";
        if (username.isBlank()) return "Username cannot be empty.";
        if (username.length() > MAX_USERNAME_LENGTH) return "Username must be 20 characters or fewer.";
        return null;
    }

    public static String validateMessage(String message) {
        if (message == null) return "Message is required.";
        if (message.isBlank()) return "Message cannot be empty.";
        if (message.length() > MAX_MESSAGE_LENGTH) return "Message must be 500 characters or fewer.";
        return null;
    }
}
