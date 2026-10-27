package network.foodsharing.testsupport;

import java.time.Clock;
import java.time.Instant;
import network.foodsharing.config.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** For slice tests: the application clock fixed at {@link #SEED_NOW}. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfig {

    /** Seed time anchor (docs/conventions.md A6). */
    public static final Instant SEED_NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Bean
    public MutableClock clock() {
        return new MutableClock(Clock.systemUTC(), SEED_NOW);
    }
}
