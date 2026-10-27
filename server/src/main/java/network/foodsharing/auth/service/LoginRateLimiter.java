package network.foodsharing.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.repository.LoginTokenRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import org.springframework.stereotype.Service;

/**
 * Limits magic-link requests (reference {@code LoginRateLimiter}, generalised): per email the stored login tokens
 * of the window are counted (survives restarts, default 3 per 15 min); per IP an in-memory sliding window
 * (default 10 per 15 min).
 */
@Service
public class LoginRateLimiter {

    private final LoginTokenRepository loginTokens;
    private final AppProperties properties;
    private final Clock clock;
    private final SlidingWindowRateLimiter ipLimiter;

    public LoginRateLimiter(LoginTokenRepository loginTokens, AppProperties properties, Clock clock) {
        this.loginTokens = loginTokens;
        this.properties = properties;
        this.clock = clock;
        this.ipLimiter = new SlidingWindowRateLimiter(clock, properties.getAuth().getMaxLoginRequestsPerIp(),
                window(properties));
    }

    /**
     * @param email normalized email
     * @throws ApiException 429 TOO_MANY_LOGIN_REQUESTS (with Retry-After)
     */
    public void check(String email, String clientIp) {
        Duration window = window(properties);
        if (clientIp != null && !ipLimiter.tryAcquire(clientIp)) {
            throw tooMany(window);
        }
        Instant windowStart = clock.instant().minus(window);
        if (loginTokens.countByEmailAndCreatedAtAfter(email, windowStart)
                >= properties.getAuth().getMaxLoginRequestsPerEmail()) {
            throw tooMany(window);
        }
    }

    private static Duration window(AppProperties properties) {
        return Duration.ofMinutes(properties.getAuth().getLoginRequestWindowMinutes());
    }

    private static ApiException tooMany(Duration window) {
        return ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_LOGIN_REQUESTS, window);
    }
}
