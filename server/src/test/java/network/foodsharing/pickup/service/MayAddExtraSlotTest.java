package network.foodsharing.pickup.service;

import static network.foodsharing.pickup.service.ExtraSlotTest.FIRST;
import static network.foodsharing.pickup.service.ExtraSlotTest.NOW;
import static network.foodsharing.pickup.service.ExtraSlotTest.PICKUP;
import static network.foodsharing.pickup.service.ExtraSlotTest.SECOND;
import static network.foodsharing.pickup.service.ExtraSlotTest.slot;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** UserPickup.mayAddExtraSlot uses the same conditions as addExtraSlot (AU6). */
class MayAddExtraSlotTest {

    @Test
    void onlyTheEarliestSignupOfAFuturePickupInAnAllowingStoreWithRoomLeft() {
        assertThat(PickupUserService.mayAddExtraSlot(true, slot(2), FIRST, NOW, false, 10)).isTrue();
        assertThat(PickupUserService.mayAddExtraSlot(true, slot(2), SECOND, NOW, false, 10)).as("not first").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(false, slot(2), FIRST, NOW, false, 10)).as("store").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(true, slot(2), FIRST, NOW, true, 10)).as("added").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(true, slot(10), FIRST, NOW, false, 10)).as("max").isFalse();
        assertThat(PickupUserService.mayAddExtraSlot(true, slot(2), FIRST, PICKUP, false, 10)).as("past").isFalse();
    }
}
