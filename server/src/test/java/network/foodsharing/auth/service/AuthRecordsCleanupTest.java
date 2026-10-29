package network.foodsharing.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.EmailChange;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.IpBlock;
import network.foodsharing.auth.domain.RegistrationAttempt;
import network.foodsharing.auth.domain.RegistrationOutcome;
import network.foodsharing.auth.repository.AuthSessionRepository;
import network.foodsharing.auth.repository.EmailChangeRepository;
import network.foodsharing.auth.repository.IpBlockRepository;
import network.foodsharing.auth.repository.RegistrationAttemptRepository;
import network.foodsharing.testsupport.JpaSliceTest;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** D17.3 retention: ended sessions, expired email changes, ended IP blocks and old sign-up attempts are removed. */
@JpaSliceTest
@Import(AuthRecordsCleanup.class)
class AuthRecordsCleanupTest {

    @Autowired
    private AuthRecordsCleanup cleanup;

    @Autowired
    private AuthSessionRepository sessions;

    @Autowired
    private EmailChangeRepository emailChanges;

    @Autowired
    private IpBlockRepository ipBlocks;

    @Autowired
    private RegistrationAttemptRepository attempts;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock clock;

    @Autowired
    private jakarta.persistence.EntityManager em;

    private int counter;

    @Test
    void removesRecordsPastTheirRetention() {
        Instant now = clock.instant();
        User user = users.saveAndFlush(TestUsers.user("cleanup@test.local"));
        AuthSession active = session(user, now.plus(Duration.ofDays(5)), null);
        AuthSession recentlyRevoked = session(user, now.plus(Duration.ofDays(5)), now.minus(Duration.ofDays(29)));
        AuthSession longRevoked = session(user, now.plus(Duration.ofDays(5)), now.minus(Duration.ofDays(31)));
        AuthSession longExpired = session(user, now.minus(Duration.ofDays(31)), null);
        EmailChange recentChange = emailChange(user, now.minus(Duration.ofDays(29)));
        EmailChange oldChange = emailChange(user, now.minus(Duration.ofDays(31)));
        IpBlock forever = ipBlock(null);
        IpBlock ended = ipBlock(now.minus(Duration.ofDays(31)));
        RegistrationAttempt attempt = attempt();

        assertThat(cleanup.cleanup(now)).isEqualTo(4);
        em.clear();
        assertThat(sessions.findAll()).extracting(AuthSession::getId)
                .containsExactlyInAnyOrder(active.getId(), recentlyRevoked.getId());
        assertThat(sessions.findById(longRevoked.getId())).isEmpty();
        assertThat(sessions.findById(longExpired.getId())).isEmpty();
        assertThat(emailChanges.findById(recentChange.getId())).isPresent();
        assertThat(emailChanges.findById(oldChange.getId())).isEmpty();
        assertThat(ipBlocks.findById(forever.getId())).isPresent();
        assertThat(ipBlocks.findById(ended.getId())).isEmpty();
        assertThat(attempts.findById(attempt.getId())).isPresent();

        // sign-up attempts are kept 90 days after they were recorded
        assertThat(cleanup.cleanup(now.plus(Duration.ofDays(91)))).isGreaterThanOrEqualTo(1);
        em.clear();
        assertThat(attempts.findById(attempt.getId())).isEmpty();
    }

    private AuthSession session(User user, Instant expiresAt, Instant revokedAt) {
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(hash());
        session.setLastSeenAt(clock.instant());
        session.setExpiresAt(expiresAt);
        session.setRevokedAt(revokedAt);
        return sessions.saveAndFlush(session);
    }

    private EmailChange emailChange(User user, Instant expiresAt) {
        EmailChange change = new EmailChange();
        change.setUserId(user.getId());
        change.setOldEmail("old@test.local");
        change.setNewEmail("new@test.local");
        change.setTokenHash(hash());
        change.setAbortTokenHash(hash());
        change.setExpiresAt(expiresAt);
        return emailChanges.saveAndFlush(change);
    }

    private IpBlock ipBlock(Instant untilAt) {
        IpBlock block = new IpBlock();
        block.setCidr("192.0.2." + (++counter) + "/32");
        block.setUntilAt(untilAt);
        return ipBlocks.saveAndFlush(block);
    }

    private RegistrationAttempt attempt() {
        RegistrationAttempt attempt = new RegistrationAttempt();
        attempt.setEmailHash(hash());
        attempt.setOutcome(RegistrationOutcome.values()[0]);
        attempt.setProvider(IdentityProvider.values()[0]);
        return attempts.saveAndFlush(attempt);
    }

    private String hash() {
        return String.format("%064d", ++counter);
    }
}
