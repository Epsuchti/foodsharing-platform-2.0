package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import network.foodsharing.api.model.Role;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** C1: verification needs a confirmed identity, for every role (no ORGA exception). */
class VerificationRulesTest {

    @Test
    void userWithoutConfirmedIdentityMayNotBeVerified() {
        assertThat(VerificationRules.mayVerify(TestUsers.user("lea@test.local", Role.FOODSHARER))).isFalse();
        assertThat(VerificationRules.mayVerify(TestUsers.user("orga@test.local", Role.ORGA))).isFalse();
    }

    @Test
    void userWithConfirmedIdentityMayBeVerified() {
        User user = TestUsers.user("lea@test.local", Role.FOODSHARER);
        user.setIdentityConfirmedAt(Instant.parse("2026-11-01T10:00:00Z"));
        assertThat(VerificationRules.mayVerify(user)).isTrue();
    }
}
