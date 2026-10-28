package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import network.foodsharing.pickup.service.PickupSchedule;
import network.foodsharing.pickup.service.PickupScheduleService;
import network.foodsharing.pickup.service.PickupSignupService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Extra slot for the earliest sign-up of a pickup (AU6): store switch and the add operation. */
@Service
public class ExtraSlotService {

    /** The pickup after adding, with the extra-slot record. */
    public record Added(PickupScheduleService.PickupView pickup) {
    }

    private final PickupPolicyService pickupPolicies;
    private final PickupSignupService signups;
    private final StoreService stores;
    private final HistoryService history;
    private final Clock clock;

    public ExtraSlotService(PickupPolicyService pickupPolicies, PickupSignupService signups, StoreService stores,
                            HistoryService history, Clock clock) {
        this.pickupPolicies = pickupPolicies;
        this.signups = signups;
        this.stores = stores;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 403, 404 */
    @Transactional
    public boolean setAllowed(UUID storeId, boolean allowed) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        boolean previous = stores.setExtraSlotAllowed(storeId, allowed);
        if (previous != allowed) {
            history.record(history.event(AutomationAction.FIELD_CHANGED, storeId).region(store.getRegionId())
                    .metadata("storeId", storeId.toString()).metadata("field", "extraSlotAllowed")
                    .metadata("old", previous).metadata("new", allowed).build());
        }
        return allowed;
    }

    /**
     * @throws ApiException 404, 400 PICKUP_IN_PAST, 403 NOT_FIRST_SIGNUP, 409 EXTRA_SLOT_NOT_ALLOWED /
     *                      EXTRA_SLOT_ALREADY_ADDED
     */
    @Transactional
    public Added add(UUID storeId, Instant pickupAt) {
        PickupSchedule.Slot slot = signups.addExtraSlot(storeId, pickupAt);
        PickupExtraSlot extra = signups.extraSlots(storeId, java.util.List.of(pickupAt)).get(pickupAt);
        Store store = stores.require(storeId);
        history.record(history.event(AutomationAction.EXTRA_SLOT_ADDED, storeId).region(store.getRegionId())
                .secondarySubject(extra == null ? null : extra.getUserId())
                .metadata("storeId", storeId.toString()).metadata("pickupAt", pickupAt.toString())
                .metadata("slotCount", slot.slotCount()).build());
        return new Added(new PickupScheduleService.PickupView(slot, slot.isAvailable(clock.instant()), null, extra,
                false));
    }
}
