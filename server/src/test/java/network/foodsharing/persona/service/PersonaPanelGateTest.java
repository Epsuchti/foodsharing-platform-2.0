package network.foodsharing.persona.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.testsupport.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Decision 173: password check and the per-IP failure window, on a fixed clock. */
class PersonaPanelGateTest {

    private final MutableClock clock = new FixedClockConfig().clock();
    private PersonaPanelGate gate;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getStaging().getPersonaPanel().setPassword("foobar");
        properties.getStaging().getPersonaPanel().setMaxFailuresPerIp(3);
        properties.getStaging().getPersonaPanel().setFailureWindow(Duration.ofMinutes(15));
        gate = new PersonaPanelGate(properties, clock);
    }

    @Test
    void acceptsTheRightPasswordOnly() {
        assertThatCode(() -> gate.verify("foobar", "10.0.0.1")).doesNotThrowAnyException();
        assertUnauthorized("foobaz", "10.0.0.1");
        assertUnauthorized("", "10.0.0.1");
        assertUnauthorized(null, "10.0.0.1");
        assertUnauthorized("FOOBAR", "10.0.0.2");
    }

    @Test
    void blocksAnIpAfterTooManyFailuresUntilTheOldestLeavesTheWindow() {
        assertUnauthorized("x", "10.0.0.1");
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(5)));
        assertUnauthorized("x", "10.0.0.1");
        assertUnauthorized("x", "10.0.0.1");

        // 4th attempt: blocked, even with the right password; Retry-After until the first failure is 15 min old
        assertThatThrownBy(() -> gate.verify("foobar", "10.0.0.1")).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(ex.getCode()).isEqualTo(ApiErrorCode.TOO_MANY_REQUESTS);
            assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMinutes(10));
        });
        // other IPs are not affected
        assertThatCode(() -> gate.verify("foobar", "10.0.0.9")).doesNotThrowAnyException();

        // 10 minutes later the first failure has left the window: one more try is possible
        clock.fixAt(clock.instant().plus(Duration.ofMinutes(10)));
        assertThatCode(() -> gate.verify("foobar", "10.0.0.1")).doesNotThrowAnyException();
        assertUnauthorized("x", "10.0.0.1");
        assertThatThrownBy(() -> gate.verify("foobar", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private void assertUnauthorized(String password, String ip) {
        assertThatThrownBy(() -> gate.verify(password, ip)).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(ex.getCode()).isEqualTo(ApiErrorCode.PERSONA_PANEL_PASSWORD_INVALID);
        });
    }
}
