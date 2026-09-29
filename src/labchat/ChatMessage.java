package labchat;

import java.time.Instant;

/** One chat entry. System messages have no user name. */
public record ChatMessage(String username, String message, Instant timestamp, boolean system) {
}
