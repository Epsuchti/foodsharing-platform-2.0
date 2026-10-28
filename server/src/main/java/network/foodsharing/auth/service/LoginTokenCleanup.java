package network.foodsharing.auth.service;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.auth.repository.LoginTokenRepository;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.history.NoHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth part of the {@code cleanup} job (§6.3): magic-link tokens expired for more than a day (the login rate limiter
 * only looks at the last hour).
 */
@Service
public class LoginTokenCleanup implements CleanupTask {

    static final Duration GRACE = Duration.ofDays(1);

    private final LoginTokenRepository tokens;

    public LoginTokenCleanup(LoginTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public String name() {
        return "login-tokens";
    }

    @Override
    @Transactional
    @NoHistory(reason = "retention housekeeping of expired one-time tokens (cleanup job)")
    public int cleanup(Instant now) {
        return tokens.deleteExpiredBefore(now.minus(GRACE));
    }
}
