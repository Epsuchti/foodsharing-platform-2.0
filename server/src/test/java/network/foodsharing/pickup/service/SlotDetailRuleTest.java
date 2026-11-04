package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PU3 slot dialog (decision 165): rich details for managers and co-pickups only. */
class SlotDetailRuleTest {

    @Test
    void managersMayView() {
        assertThat(SlotDetailRule.mayView(true, false, false)).as("confirm").isTrue();
        assertThat(SlotDetailRule.mayView(false, true, false)).as("edit").isTrue();
    }

    @Test
    void peopleSignedUpForTheSamePickupMayView() {
        assertThat(SlotDetailRule.mayView(false, false, true)).isTrue();
    }

    @Test
    void otherTeamMembersMayNot() {
        assertThat(SlotDetailRule.mayView(false, false, false)).isFalse();
    }
}
