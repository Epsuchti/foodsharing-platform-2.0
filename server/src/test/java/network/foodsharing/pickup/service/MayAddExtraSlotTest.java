package network.foodsharing.pickup.service;

import static network.foodsharing.pickup.service.ExtraSlotTest.FIRST;
import static network.foodsharing.pickup.service.ExtraSlotTest.NOW;
import static network.foodsharing.pickup.service.ExtraSlotTest.PICKUP;
import static network.foodsharing.pickup.service.ExtraSlotTest.SECOND;
import static network.foodsharing.pickup.service.ExtraSlotTest.slot;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** UserPickup.mayAddExtraSlot uses the same conditions as addExtraSlot (AU6, {@link ExtraSlotRule}). */
class MayAddExtraSlotTest {

    @Test
    void onlyTheEarliestSignupOfAFuturePickupBelowTheStoreLimitWithRoomLeft() {
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(2), FIRST, NOW, 0, 10)).isTrue();
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(2), FIRST, NOW, 1, 10)).as("one left").isTrue();
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(2), SECOND, NOW, 0, 10)).as("not first").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(0, slot(2), FIRST, NOW, 0, 10)).as("off").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(2), FIRST, NOW, 2, 10)).as("limit").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(10), FIRST, NOW, 0, 10)).as("max").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(2, slot(2), FIRST, PICKUP, 0, 10)).as("past").isFalse();
    }
}
