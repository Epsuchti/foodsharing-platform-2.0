package network.foodsharing.auth.service;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.auth.repository.AuthSessionRepository;
import network.foodsharing.auth.repository.EmailChangeRepository;
import network.foodsharing.auth.repository.IpBlockRepository;
import network.foodsharing.auth.repository.RegistrationAttemptRepository;
import network.foodsharing.common.jobs.CleanupTask;
import network.foodsharing.history.NoHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth part of the {@code cleanup} job (D17.3, {@code docs/privacy/retention.md}): sessions (user agent, IP hash)
 * 30 days after they expired or were revoked, email-change requests (old and new address) 30 days after their link
 * expired, IP blocks 30 days after they ended, sign-up attempts (email and IP hashes) after 90 days.
 */
@Service
public class AuthRecordsCleanup implements CleanupTask {

    static final Duration SESSION_RETENTION = Duration.ofDays(30);
    static final Duration EMAIL_CHANGE_RETENTION = Duration.ofDays(30);
    static final Duration IP_BLOCK_RETENTION = Duration.ofDays(30);
    static final Duration REGISTRATION_ATTEMPT_RETENTION = Duration.ofDays(90);

    private final AuthSessionRepository sessions;
    private final EmailChangeRepository emailChanges;
    private final RegistrationAttemptRepository attempts;
    private final IpBlockRepository ipBlocks;

    public AuthRecordsCleanup(AuthSessionRepository sessions, EmailChangeRepository emailChanges,
                              RegistrationAttemptRepository attempts, IpBlockRepository ipBlocks) {
        this.sessions = sessions;
        this.emailChanges = emailChanges;
        this.attempts = attempts;
        this.ipBlocks = ipBlocks;
    }

    @Override
    public String name() {
        return "auth-records";
    }

    @Override
    @Transactional
    @NoHistory(reason = "retention housekeeping of ended sessions, email changes, IP blocks and sign-up attempts")
    public int cleanup(Instant now) {
        return sessions.deleteEndedBefore(now.minus(SESSION_RETENTION))
                + emailChanges.deleteExpiredBefore(now.minus(EMAIL_CHANGE_RETENTION))
                + ipBlocks.deleteEndedBefore(now.minus(IP_BLOCK_RETENTION))
                + attempts.deleteCreatedBefore(now.minus(REGISTRATION_ATTEMPT_RETENTION));
    }
}
