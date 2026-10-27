package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.repository.LoginTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import network.foodsharing.testsupport.FixedClockConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final LoginTokenRepository tokens = mock(LoginTokenRepository.class);
    private final MutableClock clock = new FixedClockConfig().clock();
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getAuth().setMaxLoginRequestsPerIp(2);
        properties.getAuth().setMaxLoginRequestsPerEmail(3);
        properties.getAuth().setLoginRequestWindowMinutes(15);
        limiter = new LoginRateLimiter(tokens, properties, clock);
    }

    @Test
    void limitsPerIpInMemoryAndForgetsAfterTheWindow() {
        limiter.check("a@example.org", "10.0.0.1");
        limiter.check("b@example.org", "10.0.0.1");
        assertThatThrownBy(() -> limiter.check("c@example.org", "10.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getCode())
                            .isEqualTo(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS);
                    org.assertj.core.api.Assertions.assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofMinutes(15));
                });
        assertThatCode(() -> limiter.check("c@example.org", "10.0.0.2")).doesNotThrowAnyException();

        clock.fixAt(FixedClockConfig.SEED_NOW.plus(Duration.ofMinutes(16)));
        assertThatCode(() -> limiter.check("c@example.org", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void limitsPerEmailByStoredTokensOfTheWindow() {
        Instant windowStart = FixedClockConfig.SEED_NOW.minus(Duration.ofMinutes(15));
        when(tokens.countByEmailAndCreatedAtAfter(eq("busy@example.org"), any())).thenReturn(3L);
        when(tokens.countByEmailAndCreatedAtAfter(eq("calm@example.org"), any())).thenReturn(2L);

        assertThatThrownBy(() -> limiter.check("busy@example.org", null)).isInstanceOf(ApiException.class);
        assertThatCode(() -> limiter.check("calm@example.org", null)).doesNotThrowAnyException();
        verify(tokens).countByEmailAndCreatedAtAfter("calm@example.org", windowStart);
    }
}
