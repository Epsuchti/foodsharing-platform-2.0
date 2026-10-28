package network.foodsharing.common.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import network.foodsharing.config.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/** A26: the breaker state machine (port of the reference FoodsharingAvailability) with a fixed clock. */
class CircuitBreakerTest {

    private static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");

    private final MutableClock clock = new MutableClock(Clock.fixed(T0, ZoneOffset.UTC), T0);
    private final AtomicBoolean reachable = new AtomicBoolean(false);
    private final AtomicInteger probes = new AtomicInteger();
    private CircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        breaker = new CircuitBreaker(Integration.TELEGRAM, clock, Duration.ofMinutes(5), Duration.ofDays(3));
        breaker.setProbe(() -> {
            probes.incrementAndGet();
            return reachable.get();
        });
    }

    @Test
    void resetClosesTheBreakerAndForgetsOutages() {
        breaker.forceOpen("dev", "simulated outage");
        assertThat(breaker.shouldSkip()).isTrue();

        breaker.reset();

        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.shouldSkip()).isFalse();
        assertThat(breaker.snapshot().failureCount()).isZero();
        assertThat(breaker.snapshot().lastError()).isNull();
        assertThat(breaker.outageOverlap(T0.minus(Duration.ofDays(1)), T0.plus(Duration.ofDays(1)))).isZero();
    }

    @Test
    void onlyOutagesConfirmedByAProbeOpenTheBreaker() {
        assertThat(breaker.reportFailure("send", new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(probes).hasValue(0); // a 4xx is our fault, not an outage
        reachable.set(true);
        assertThat(breaker.reportFailure("send", new ResourceAccessException("timeout"))).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.snapshot().failureCount()).isEqualTo(2);

        reachable.set(false);
        assertThat(breaker.reportFailure("send", new RuntimeException(new SocketTimeoutException("read")))).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.snapshot().nextProbeAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    void openBreakerSkipsUntilTheNextProbeAndClosesWhenItSucceeds() {
        breaker.reportFailure("send", new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(breaker.shouldSkip()).isTrue();
        int probesWhileOpen = probes.get();

        clock.fixAt(T0.plus(Duration.ofMinutes(4)));
        assertThat(breaker.shouldSkip()).isTrue();
        assertThat(probes).hasValue(probesWhileOpen); // no probe before nextProbeAt

        clock.fixAt(T0.plus(Duration.ofMinutes(5)));
        assertThat(breaker.state()).isEqualTo(CircuitState.HALF_OPEN);
        assertThat(breaker.shouldSkip()).isTrue(); // probe failed: open for another interval
        assertThat(breaker.snapshot().nextProbeAt()).isEqualTo(T0.plus(Duration.ofMinutes(10)));

        reachable.set(true);
        clock.fixAt(T0.plus(Duration.ofMinutes(10)));
        assertThat(breaker.shouldSkip()).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.snapshot().downSince()).isNull();
    }

    @Test
    void successClosesAndResetsTheFailureCount() {
        breaker.reportFailure("send", new ResourceAccessException("down"));
        breaker.markSucceeded();
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.snapshot().failureCount()).isZero();
        assertThat(breaker.snapshot().lastSuccessAt()).isEqualTo(T0);
    }

    @Test
    void outagesExtendDeadlines() {
        breaker.markSucceeded(); // last known up at T0
        clock.fixAt(T0.plus(Duration.ofMinutes(10)));
        breaker.reportFailure("send", new ResourceAccessException("down")); // down since T0 (last ok)
        reachable.set(true);
        clock.fixAt(T0.plus(Duration.ofMinutes(30)));
        assertThat(breaker.shouldSkip()).isFalse(); // closed at T0+30

        assertThat(breaker.outageOverlap(T0.minus(Duration.ofHours(1)), T0.plus(Duration.ofHours(1))))
                .isEqualTo(Duration.ofMinutes(30));
        // deadline T0+20 started at T0: 20 min outage within → T0+40, which covers the full 30 min → T0+50
        assertThat(breaker.extendDeadline(T0, T0.plus(Duration.ofMinutes(20))))
                .isEqualTo(T0.plus(Duration.ofMinutes(50)));

        clock.fixAt(T0.plus(Duration.ofDays(4)));
        breaker.markSucceeded();
        reachable.set(false);
        assertThat(breaker.reportFailure("send", new ResourceAccessException("down"))).isTrue();
        clock.fixAt(T0.plus(Duration.ofDays(4)).plus(Duration.ofMinutes(1)));
        breaker.markSucceeded(); // history older than 3 days is dropped when the next outage ends
        assertThat(breaker.outageOverlap(T0.minus(Duration.ofHours(1)), T0.plus(Duration.ofHours(1))))
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void probeNowOpensAndCloses() {
        assertThat(breaker.probeNow()).isFalse();
        assertThat(breaker.state()).isEqualTo(CircuitState.OPEN);
        reachable.set(true);
        assertThat(breaker.probeNow()).isTrue();
        assertThat(breaker.state()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void outageClassification() {
        assertThat(CircuitBreaker.looksLikeOutage(new java.net.UnknownHostException("api.telegram.org"))).isTrue();
        assertThat(CircuitBreaker.looksLikeOutage(new HttpServerErrorException(HttpStatus.BAD_GATEWAY))).isTrue();
        assertThat(CircuitBreaker.looksLikeOutage(new IllegalStateException("bug"))).isFalse();
    }
}
