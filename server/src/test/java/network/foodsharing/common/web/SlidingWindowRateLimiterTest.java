package network.foodsharing.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void limitsPerKeyAndClearAllForgetsEverything() {
        SlidingWindowRateLimiter first = new SlidingWindowRateLimiter("support", clock, 1, Duration.ofHours(1));
        SlidingWindowRateLimiter second = new SlidingWindowRateLimiter("support", clock, 1, Duration.ofHours(1));
        assertThat(first.tryAcquire("a")).isTrue();
        assertThat(first.tryAcquire("a")).isFalse();
        assertThat(first.tryAcquire("b")).isTrue();
        assertThat(second.tryAcquire("a")).isTrue();
        assertThat(second.tryAcquire("a")).isFalse();

        SlidingWindowRateLimiter.clearAll();

        assertThat(first.tryAcquire("a")).isTrue();
        assertThat(second.tryAcquire("a")).isTrue();
    }
}
