package labchat.service;

import labchat.config.ServerConfig;
import labchat.model.User;
import labchat.util.ValidationUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UserService {
    private final ServerConfig config;
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> guestTokens = new ConcurrentHashMap<>();

    public UserService(ServerConfig config) {
        this.config = config;
    }

    public synchronized String registerGuest() {
        int attempt = 0;
        while (attempt < 100) {
            String guestName = "Guest_" + (1000 + new Random().nextInt(9000));
            if (!users.containsKey(guestName)) {
                return guestName;
            }
            attempt++;
        }
        return "Guest_" + UUID.randomUUID().toString().substring(0, 4);
    }

    public synchronized boolean join(String username) {
        String problem = ValidationUtil.validateUsername(username, config.getMaxUsernameLength());
        if (problem != null) {
            return false;
        }
        User existing = users.get(username);
        if (existing != null) {
            if (existing.isBanned()) {
                return false;
            }
            // If already exists and offline/active, touch and resume
            existing.touch();
            return true;
        }

        if (users.size() >= config.getMaxUsers()) {
            return false;
        }

        users.put(username, new User(username));
        return true;
    }

    public boolean leave(String username) {
        if (username == null) return false;
        User user = users.remove(username);
        return user != null;
    }

    public User getUser(String username) {
        if (username == null) return null;
        return users.get(username);
    }

    public boolean touch(String username) {
        User user = users.get(username);
        if (user == null) return false;
        user.touch();
        return true;
    }

    public boolean setStatus(String username, String status) {
        User user = users.get(username);
        if (user == null) return false;
        user.setStatus(status);
        return true;
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>(users.values());
        list.sort(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public List<String> removeInactiveUsers() {
        Duration inactiveTimeout = Duration.ofSeconds(config.getUserInactiveTimeoutSeconds());
        Instant cutoff = Instant.now().minus(inactiveTimeout);
        List<String> removed = new ArrayList<>();

        users.forEach((name, user) -> {
            if (user.getLastSeen().isBefore(cutoff)) {
                if (users.remove(name, user)) {
                    removed.add(name);
                }
            }
        });
        removed.sort(Comparator.naturalOrder());
        return removed;
    }

    public int getActiveUserCount() {
        return users.size();
    }
}
