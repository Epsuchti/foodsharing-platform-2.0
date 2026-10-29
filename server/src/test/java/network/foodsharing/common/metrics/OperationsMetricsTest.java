package network.foodsharing.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import network.foodsharing.common.integration.CircuitState;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D19.3: job streaks, last success, breaker values and limiter rejections as exported metrics. */
class OperationsMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void attach() {
        Metrics.addRegistry(registry);
    }

    @AfterEach
    void detach() {
        Metrics.removeRegistry(registry);
    }

    @Test
    void jobFailuresCountInARowAndResetOnSuccess() {
        String job = "metrics-test-job";
        OperationsMetrics.jobFinished(job, Duration.ofMillis(5), false, Instant.ofEpochSecond(100));
        OperationsMetrics.jobFinished(job, Duration.ofMillis(5), false, Instant.ofEpochSecond(200));
        assertThat(gauge(OperationsMetrics.JOB_CONSECUTIVE_FAILURES, job)).isEqualTo(2);
        assertThat(gauge(OperationsMetrics.JOB_LAST_SUCCESS, job)).isZero();

        OperationsMetrics.jobFinished(job, Duration.ofMillis(5), true, Instant.ofEpochSecond(300));
        assertThat(gauge(OperationsMetrics.JOB_CONSECUTIVE_FAILURES, job)).isZero();
        assertThat(gauge(OperationsMetrics.JOB_LAST_SUCCESS, job)).isEqualTo(300);
        assertThat(registry.get(OperationsMetrics.JOB_DURATION).tag("name", job).tag("outcome", "FAILED").timer()
                .count()).isEqualTo(2);
    }

    @Test
    void breakerStatesMapToOrderedValues() {
        assertThat(OperationsMetrics.stateValue(CircuitState.CLOSED)).isZero();
        assertThat(OperationsMetrics.stateValue(CircuitState.HALF_OPEN)).isEqualTo(1);
        assertThat(OperationsMetrics.stateValue(CircuitState.OPEN)).isEqualTo(2);
    }

    @Test
    void onlyRejectionsOfALimiterAreCounted() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter("support", Clock.systemUTC(), 1,
                Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k")).isTrue();
        assertThat(limiter.tryAcquire("k")).isFalse();
        assertThat(limiter.tryAcquire("k")).isFalse();
        assertThat(registry.get(OperationsMetrics.RATE_LIMIT_REJECTIONS).tag("limiter", "support").counter().count())
                .isEqualTo(2);
    }

    private double gauge(String name, String job) {
        return registry.get(name).tag("name", job).gauge().value();
    }
}
