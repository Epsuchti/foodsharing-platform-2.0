package network.foodsharing.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.common.metrics.OperationsMetrics;

/**
 * In-memory sliding-window limiter keyed by e.g. an IP (pattern from the reference {@code LoginRateLimiter}).
 * Not a bean: each user creates its own instance with its own limit.
 */
public class SlidingWindowRateLimiter {

    private final String name;
    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final Map<String, Deque<Instant>> requestsByKey = new ConcurrentHashMap<>();

    /** All limiters of the application (weakly held) so the dev seed reset can clear them. */
    private static final java.util.Set<SlidingWindowRateLimiter> INSTANCES =
            java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    /** @param name metric tag of {@code fs_rate_limit_rejections_total} (one of {@link OperationsMetrics#LIMITERS}) */
    public SlidingWindowRateLimiter(String name, Clock clock, int limit, Duration window) {
        this.name = name;
        this.clock = clock;
        this.limit = limit;
        this.window = window;
        INSTANCES.add(this);
    }

    /** Forgets all recorded attempts of this limiter. */
    public void clear() {
        requestsByKey.clear();
    }

    /** Dev seed reset: clears every limiter. */
    public static void clearAll() {
        synchronized (INSTANCES) {
            INSTANCES.forEach(SlidingWindowRateLimiter::clear);
        }
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
        if (!allowed[0]) {
            OperationsMetrics.rateLimited(name);
        }
        return allowed[0];
    }

    public Duration window() {
        return window;
    }
}
