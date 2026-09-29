package labchat;

import java.time.Instant;

/** An in-memory presence record. */
public final class User {
    private final String username;
    private volatile Instant lastSeen;

    public User(String username) {
        this.username = username;
        this.lastSeen = Instant.now();
    }

    public String username() { return username; }
    public Instant lastSeen() { return lastSeen; }
    public void touch() { lastSeen = Instant.now(); }
}
