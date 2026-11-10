package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** Q3 (review 2026-11-10): the typical wait for verification counts from the last EinAb, with at least 3 cases. */
class JourneyServiceTypicalDaysTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final List<User> verified = new java.util.ArrayList<>();
    private final Map<UUID, Instant> lastIntroPickup = new HashMap<>();

    /** A user verified {@code verifiedDaysAgo} days ago whose last EinAb was {@code waitDays} before that. */
    private void verifiedAfter(int waitDays, int verifiedDaysAgo) {
        User user = verifiedWithout(verifiedDaysAgo);
        // registered long ago: the old "registration to verification" figure would be far larger
        user.setRegisteredAt(NOW.minus(Duration.ofDays(400)));
        lastIntroPickup.put(user.getId(), user.getVerifiedAt().minus(Duration.ofDays(waitDays)));
    }

    private User verifiedWithout(int verifiedDaysAgo) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setVerified(true);
        user.setVerifiedAt(NOW.minus(Duration.ofDays(verifiedDaysAgo)));
        verified.add(user);
        return user;
    }

    @Test
    void medianOfTheDaysFromTheLastIntroPickupToVerification() {
        verifiedAfter(2, 10);
        verifiedAfter(9, 20);
        verifiedAfter(5, 30);
        assertThat(JourneyService.medianDaysToVerification(verified, lastIntroPickup)).isEqualTo(5);

        verifiedAfter(7, 40);
        assertThat(JourneyService.medianDaysToVerification(verified, lastIntroPickup)).isEqualTo(6);
    }

    @Test
    void fewerThanThreeCasesGiveNull() {
        verifiedAfter(2, 10);
        verifiedAfter(4, 20);
        // verified without any counted EinAb (e.g. imported): not a case
        verifiedWithout(5);
        verifiedWithout(6);
        assertThat(JourneyService.medianDaysToVerification(verified, lastIntroPickup)).isNull();

        verifiedAfter(6, 30);
        assertThat(JourneyService.medianDaysToVerification(verified, lastIntroPickup)).isEqualTo(4);
    }

    @Test
    void anIntroPickupAfterVerificationCountsAsZeroDays() {
        verifiedAfter(-3, 10);
        verifiedAfter(0, 20);
        verifiedAfter(1, 30);
        assertThat(JourneyService.medianDaysToVerification(verified, lastIntroPickup)).isZero();
    }
}
