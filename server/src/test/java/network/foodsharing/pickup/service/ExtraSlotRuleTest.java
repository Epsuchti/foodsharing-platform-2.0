package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AU6 rule: 0 = off, store limit, cap by maxSlotsPerPickup, only the first sign-up, only future pickups. */
class ExtraSlotRuleTest {

    @Test
    void allowedBelowTheLimitForTheFirstSignupOfAFuturePickup() {
        assertThat(ExtraSlotRule.mayAdd(1, 0, 2, 10, true, true)).isTrue();
        assertThat(ExtraSlotRule.mayAdd(3, 2, 2, 10, true, true)).isTrue();
    }

    @Test
    void zeroMeansOff() {
        assertThat(ExtraSlotRule.mayAdd(0, 0, 2, 10, true, true)).isFalse();
    }

    @Test
    void limitReached() {
        assertThat(ExtraSlotRule.mayAdd(2, 2, 4, 10, true, true)).isFalse();
        assertThat(ExtraSlotRule.mayAdd(2, 3, 4, 10, true, true)).isFalse();
    }

    @Test
    void cappedByMaxSlotsPerPickup() {
        assertThat(ExtraSlotRule.mayAdd(5, 0, 9, 10, true, true)).isTrue();
        assertThat(ExtraSlotRule.mayAdd(5, 0, 10, 10, true, true)).isFalse();
    }

    @Test
    void onlyTheFirstSignup() {
        assertThat(ExtraSlotRule.mayAdd(2, 0, 2, 10, false, true)).isFalse();
    }

    @Test
    void onlyFuturePickups() {
        assertThat(ExtraSlotRule.mayAdd(2, 0, 2, 10, true, false)).isFalse();
    }
}
