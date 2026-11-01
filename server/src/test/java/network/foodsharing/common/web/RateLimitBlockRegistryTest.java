package network.foodsharing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.common.web.RateLimitBlockRegistry.Block;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import org.junit.jupiter.api.Test;

/** D17.2: escalating blocks 1, 5, 30, 30 min; quiet reset after 24 h; lift resets the level; blocked keys not counted. */
class RateLimitBlockRegistryTest {

    private static final Instant START = Instant.parse("2026-11-02T09:00:00Z");
    private static final String KEY = RateLimitBlockRegistry.ipKey("203.0.113.7");

    private final MutableClock clock = new MutableClock(java.time.Clock.fixed(START, ZoneOffset.UTC), START);

    private RateLimitBlockRegistry registry(int limit) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerIp(limit);
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerUser(limit);
        return new RateLimitBlockRegistry(properties, clock);
    }

    /** Sends requests the way the filter does until the key gets blocked; returns the new block. */
    private static Block overrun(RateLimitBlockRegistry registry, String key, int limit) {
        for (int i = 0; i < limit; i++) {
            assertThat(registry.check(key)).isEmpty();
            assertThat(registry.register(key)).as("request %d within the limit", i + 1).isEmpty();
        }
        assertThat(registry.check(key)).isEmpty();
        return registry.register(key).orElseThrow();
    }

    @Test
    void overTheLimitEscalatesOneFiveThirtyThirty() {
        RateLimitBlockRegistry registry = registry(3);
        int[] expectedMinutes = {1, 5, 30, 30};
        for (int i = 0; i < expectedMinutes.length; i++) {
            Block block = overrun(registry, KEY, 3);
            assertThat(block.level()).isEqualTo(i + 1);
            assertThat(block.scope()).isEqualTo(RateLimitBlockRegistry.Scope.IP);
            assertThat(block.subject()).isEqualTo("203.0.113.7");
            assertThat(block.duration()).isEqualTo(Duration.ofMinutes(expectedMinutes[i]));
            assertThat(registry.check(KEY)).contains(Duration.ofMinutes(expectedMinutes[i]));
            clock.fixAt(block.expiresAt());
            assertThat(registry.check(KEY)).as("block ends at expiresAt").isEmpty();
            clock.fixAt(clock.instant().plusSeconds(61)); // past the sliding window
        }
    }

    @Test
    void theLevelIsBackAtOneAfter24QuietHours() {
        RateLimitBlockRegistry registry = registry(2);
        Block first = overrun(registry, KEY, 2);
        clock.fixAt(first.expiresAt().plusSeconds(61));
        Block second = overrun(registry, KEY, 2);
        assertThat(second.level()).isEqualTo(2);

        clock.fixAt(second.expiresAt().plus(Duration.ofHours(24)).plusSeconds(1));
        assertThat(overrun(registry, KEY, 2).level()).isEqualTo(1);
    }

    @Test
    void liftEndsTheBlockAndResetsTheLevel() {
        RateLimitBlockRegistry registry = registry(2);
        Block first = overrun(registry, KEY, 2);
        clock.fixAt(first.expiresAt().plusSeconds(61));
        Block second = overrun(registry, KEY, 2);
        assertThat(registry.list()).extracting(Block::id).containsExactly(second.id());

        assertThat(registry.lift(second.id())).contains(second);
        assertThat(registry.check(KEY)).isEmpty();
        assertThat(registry.list()).isEmpty();
        assertThat(registry.lift(second.id())).as("already lifted").isEmpty();
        assertThat(registry.lift(UUID.randomUUID())).isEmpty();

        // the counted requests are forgotten too: the full limit is available again, then level 1
        assertThat(overrun(registry, KEY, 2).level()).isEqualTo(1);
    }

    @Test
    void blockedKeysAreRejectedWithoutBeingCounted() {
        RateLimitBlockRegistry registry = registry(2);
        Block block = overrun(registry, KEY, 2);
        for (int i = 0; i < 50; i++) {
            assertThat(registry.check(KEY)).isPresent();
        }
        // after the block (and the sliding window) the key has its full limit again: the rejections were not counted
        clock.fixAt(block.expiresAt().plusSeconds(61));
        assertThat(overrun(registry, KEY, 2).level()).isEqualTo(2);
    }

    @Test
    void accountsAndIpsAreSeparateAndALimitOfZeroTurnsItsPartOff() {
        RateLimitBlockRegistry registry = registry(1);
        UUID user = UUID.randomUUID();
        String userKey = RateLimitBlockRegistry.userKey(user);
        Block block = overrun(registry, userKey, 1);
        assertThat(block.scope()).isEqualTo(RateLimitBlockRegistry.Scope.ACCOUNT);
        assertThat(block.userId()).contains(user);
        assertThat(registry.check(KEY)).isEmpty();
        assertThat(registry.register(KEY)).isEmpty();

        AppProperties properties = new AppProperties();
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerIp(0);
        properties.getSecurity().getApiRateLimit().setRequestsPerMinutePerUser(0);
        RateLimitBlockRegistry off = new RateLimitBlockRegistry(properties, clock);
        assertThat(off.enabled()).isFalse();
        for (int i = 0; i < 1000; i++) {
            assertThat(off.register(KEY)).isEmpty();
        }
    }

    @Test
    void createBlocksAtTheNextLevelAndClearForgetsEverything() {
        RateLimitBlockRegistry registry = registry(300);
        Block first = registry.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.1");
        assertThat(first.level()).isEqualTo(1);
        assertThat(registry.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.1")).as("still active").isEqualTo(first);
        clock.fixAt(first.expiresAt());
        assertThat(registry.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.1").level()).isEqualTo(2);

        registry.resetInMemoryState();
        assertThat(registry.list()).isEmpty();
        assertThat(registry.create(RateLimitBlockRegistry.Scope.IP, "198.51.100.1").level()).isEqualTo(1);
        assertThat(registry.check(RateLimitBlockRegistry.ipKey("198.51.100.1"))).isNotEqualTo(Optional.empty());
    }
}
