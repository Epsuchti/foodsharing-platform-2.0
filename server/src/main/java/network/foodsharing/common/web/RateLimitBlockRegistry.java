package network.foodsharing.common.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.common.metrics.OperationsMetrics;
import network.foodsharing.common.seed.InMemoryState;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * D17.2: escalating blocks of the API rate limit ({@link ApiRateLimitFilter}). Requests are counted per account
 * ({@code user:<uuid>}) or per client IP ({@code ip:<addr>}) in two sliding windows ({@code api-user},
 * {@code api-ip}); going over the limit blocks the key for the next of {@code blockSteps} (1, 5, then 30 minutes by
 * default; the last step repeats). The level starts again at 1 when the last block ended more than
 * {@code quietReset} ago or when an admin lifts the block. Kept only in memory (one app instance, decision 81).
 */
@Component
public class RateLimitBlockRegistry implements InMemoryState {

    /** Above this many keys, expired blocks and levels are dropped (as in {@link SlidingWindowRateLimiter}). */
    static final int MAX_KEYS = 10_000;

    public enum Scope {
        IP("ip:", "api-ip"),
        ACCOUNT("user:", "api-user");

        private final String prefix;
        private final String limiterName;

        Scope(String prefix, String limiterName) {
            this.prefix = prefix;
            this.limiterName = limiterName;
        }

        public String key(String subject) {
            return prefix + subject;
        }

        static Scope ofKey(String key) {
            return key.startsWith(ACCOUNT.prefix) ? ACCOUNT : IP;
        }
    }

    /** An active block; {@code subject} is the IP address or the user id. */
    public record Block(UUID id, Scope scope, String subject, int level, Instant blockedAt, Instant expiresAt) {

        public String key() {
            return scope.key(subject);
        }

        /** The blocked account, for {@link Scope#ACCOUNT}. */
        public Optional<UUID> userId() {
            return scope == Scope.ACCOUNT ? Optional.of(UUID.fromString(subject)) : Optional.empty();
        }

        public Duration duration() {
            return Duration.between(blockedAt, expiresAt);
        }
    }

    private record Level(int level, Instant lastBlockEnd) {
    }

    private final Clock clock;
    private final SlidingWindowRateLimiter ipLimiter;
    private final SlidingWindowRateLimiter userLimiter;
    private final List<Duration> steps;
    private final Duration quietReset;
    private final Map<String, Block> blocks = new ConcurrentHashMap<>();
    private final Map<String, Level> levels = new ConcurrentHashMap<>();

    public RateLimitBlockRegistry(AppProperties properties, Clock clock) {
        this.clock = clock;
        // slice tests mock AppProperties (as PublicApiRateLimitFilter does: fall back to the defaults)
        AppProperties.Security security = properties.getSecurity() == null
                ? new AppProperties.Security() : properties.getSecurity();
        AppProperties.Security.ApiRateLimit config = security.getApiRateLimit();
        this.ipLimiter = limiter(Scope.IP, clock, config.getRequestsPerMinutePerIp());
        this.userLimiter = limiter(Scope.ACCOUNT, clock, config.getRequestsPerMinutePerUser());
        this.steps = config.getBlockSteps() == null || config.getBlockSteps().isEmpty()
                ? new AppProperties.Security.ApiRateLimit().getBlockSteps() : List.copyOf(config.getBlockSteps());
        this.quietReset = config.getQuietReset();
    }

    private static SlidingWindowRateLimiter limiter(Scope scope, Clock clock, int limit) {
        return limit > 0 ? new SlidingWindowRateLimiter(scope.limiterName, clock, limit, Duration.ofMinutes(1)) : null;
    }

    public static String ipKey(String ip) {
        return Scope.IP.key(ip);
    }

    public static String userKey(UUID userId) {
        return Scope.ACCOUNT.key(userId.toString());
    }

    /** False when both limits are 0: then nothing is counted or blocked. */
    public boolean enabled() {
        return ipLimiter != null || userLimiter != null;
    }

    /** The remaining block time of {@code key}, or empty when it is not blocked. Blocked requests are not counted. */
    public Optional<Duration> check(String key) {
        Block block = blocks.get(key);
        if (block == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (!block.expiresAt().isAfter(now)) {
            blocks.remove(key, block);
            return Optional.empty();
        }
        OperationsMetrics.rateLimited(block.scope().limiterName);
        return Optional.of(Duration.between(now, block.expiresAt()));
    }

    /**
     * Counts a request of {@code key}; when it goes over the limit, blocks the key at the next level and returns the
     * new block (the caller records it in the history).
     */
    public Optional<Block> register(String key) {
        SlidingWindowRateLimiter limiter = Scope.ofKey(key) == Scope.ACCOUNT ? userLimiter : ipLimiter;
        if (limiter == null || limiter.tryAcquire(key)) {
            return Optional.empty();
        }
        return Optional.of(block(key));
    }

    /** Blocks {@code subject} at its next level (dev endpoint for e2e). */
    public Block create(Scope scope, String subject) {
        return block(scope.key(subject));
    }

    /** Active blocks, newest first. */
    public List<Block> list() {
        Instant now = clock.instant();
        return blocks.values().stream().filter(block -> block.expiresAt().isAfter(now))
                .sorted(Comparator.comparing(Block::blockedAt).reversed().thenComparing(Block::key)).toList();
    }

    /** Ends an active block and resets its level; empty when there is no such active block. */
    public synchronized Optional<Block> lift(UUID id) {
        Instant now = clock.instant();
        Optional<Block> block = blocks.values().stream()
                .filter(candidate -> candidate.id().equals(id) && candidate.expiresAt().isAfter(now)).findFirst();
        block.ifPresent(found -> {
            blocks.remove(found.key());
            levels.remove(found.key());
            SlidingWindowRateLimiter limiter = found.scope() == Scope.ACCOUNT ? userLimiter : ipLimiter;
            if (limiter != null) {
                limiter.clear(found.key());
            }
        });
        return block;
    }

    /** Forgets all blocks, levels and counted requests. */
    public synchronized void clear() {
        blocks.clear();
        levels.clear();
        if (ipLimiter != null) {
            ipLimiter.clear();
        }
        if (userLimiter != null) {
            userLimiter.clear();
        }
    }

    @Override
    public void resetInMemoryState() {
        clear();
    }

    private synchronized Block block(String key) {
        Instant now = clock.instant();
        Block active = blocks.get(key);
        if (active != null && active.expiresAt().isAfter(now)) {
            return active;
        }
        Level previous = levels.get(key);
        int level = previous == null || previous.lastBlockEnd().plus(quietReset).isBefore(now)
                ? 1 : previous.level() + 1;
        Duration duration = steps.get(Math.min(level, steps.size()) - 1);
        Scope scope = Scope.ofKey(key);
        Block block = new Block(UUID.randomUUID(), scope, key.substring(scope.prefix.length()), level, now,
                now.plus(duration));
        blocks.put(key, block);
        levels.put(key, new Level(level, block.expiresAt()));
        cleanUp(now);
        OperationsMetrics.rateLimitBlocked(scope.name(), level);
        return block;
    }

    private void cleanUp(Instant now) {
        if (blocks.size() > MAX_KEYS) {
            blocks.values().removeIf(block -> !block.expiresAt().isAfter(now));
        }
        if (levels.size() > MAX_KEYS) {
            levels.values().removeIf(level -> level.lastBlockEnd().plus(quietReset).isBefore(now));
        }
    }
}
