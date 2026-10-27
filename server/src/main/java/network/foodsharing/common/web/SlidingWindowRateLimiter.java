package network.foodsharing.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter keyed by e.g. an IP (pattern from the reference {@code LoginRateLimiter}).
 * Not a bean: each user creates its own instance with its own limit.
 */
public class SlidingWindowRateLimiter {

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> requestsByKey = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(Clock clock, int limit, Duration window) {
        this.clock = clock;
        this.limit = limit;
        this.window = window;
    }

    /** Records an attempt; returns false when the key is over the limit. */
    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(window);
        boolean[] allowed = {false};
        requestsByKey.compute(key, (ignored, requests) -> {
            Deque<Instant> recent = requests == null ? new ArrayDeque<>() : requests;
            while (!recent.isEmpty() && recent.peekFirst().isBefore(windowStart)) {
                recent.pollFirst();
            }
            if (recent.size() < limit) {
                recent.addLast(now);
                allowed[0] = true;
            }
            return recent;
        });
        if (requestsByKey.size() > 10_000) {
            requestsByKey.values().removeIf(requests -> requests.isEmpty() || requests.peekLast().isBefore(windowStart));
        }
        return allowed[0];
    }

    public Duration window() {
        return window;
    }
}
