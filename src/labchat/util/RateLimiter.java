package labchat.util;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RateLimiter {
    private final int maxPerWindow;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> clients = new ConcurrentHashMap<>();

    private static class Window {
        long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.windowStart = start;
            this.count.set(1);
        }
    }

    public RateLimiter(int maxPerWindow, long windowMillis) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String clientId) {
        if (clientId == null || clientId.isBlank()) clientId = "anonymous";
        long now = System.currentTimeMillis();
        Window win = clients.compute(clientId, (k, existing) -> {
            if (existing == null || now - existing.windowStart > windowMillis) {
                return new Window(now);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        // Periodic cleanup
        if (clients.size() > 5000) {
            clients.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMillis * 2);
        }

        return win.count.get() <= maxPerWindow;
    }
}
