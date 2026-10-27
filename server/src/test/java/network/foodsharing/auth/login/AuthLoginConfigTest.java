package network.foodsharing.auth.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import network.foodsharing.config.MutableClock;
import org.junit.jupiter.api.Test;

class AuthLoginConfigTest {

    private static final Instant REAL_NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant SEED_NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Test
    void idTokensAreValidatedAgainstTheRealTimeWhenTheAppClockIsFixed() {
        MutableClock appClock = new MutableClock(Clock.fixed(REAL_NOW, ZoneOffset.UTC), SEED_NOW);

        Clock tokenClock = AuthLoginConfig.tokenClock(appClock);

        assertThat(appClock.instant()).isEqualTo(SEED_NOW);
        assertThat(tokenClock.instant()).isEqualTo(REAL_NOW);
    }

    @Test
    void otherClocksAreUsedAsTheyAre() {
        Clock clock = Clock.fixed(REAL_NOW, ZoneOffset.UTC);

        assertThat(AuthLoginConfig.tokenClock(clock)).isSameAs(clock);
    }
}
