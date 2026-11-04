package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.pickup.service.ExtraSlotRule;
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
    private final int maxSlotsPerPickup;

    public ExtraSlotService(PickupPolicyService pickupPolicies, PickupSignupService signups, StoreService stores,
                            HistoryService history, Clock clock, AppProperties properties) {
        this.maxSlotsPerPickup = properties.getStore().getMaxSlotsPerPickup();
        this.pickupPolicies = pickupPolicies;
        this.signups = signups;
        this.stores = stores;
        this.history = history;
        this.clock = clock;
    }

    /** @throws ApiException 403, 404, 400 VALIDATION_FAILED (outside 0..50) */
    @Transactional
    public int setMaxExtraSlots(UUID storeId, int maxExtraSlots) {
        if (maxExtraSlots < 0 || maxExtraSlots > 50) {
            throw ApiException.badRequest(network.foodsharing.api.model.ApiErrorCode.VALIDATION_FAILED);
        }
        Store store = pickupPolicies.requireAutomationStore(storeId);
        int previous = stores.setMaxExtraSlots(storeId, maxExtraSlots);
        if (previous != maxExtraSlots) {
            history.record(history.event(AutomationAction.FIELD_CHANGED, storeId).region(store.getRegionId())
                    .metadata("storeId", storeId.toString()).metadata("field", "maxExtraSlots")
                    .metadata("old", previous).metadata("new", maxExtraSlots).build());
        }
        return maxExtraSlots;
    }

    /**
     * @throws ApiException 404, 400 PICKUP_IN_PAST, 403 NOT_FIRST_SIGNUP, 409 EXTRA_SLOT_NOT_ALLOWED /
     *                      EXTRA_SLOT_LIMIT_REACHED
     */
    @Transactional
    public Added add(UUID storeId, Instant pickupAt) {
        PickupSchedule.Slot slot = signups.addExtraSlot(storeId, pickupAt);
        List<PickupExtraSlot> extras = signups.extraSlots(storeId, List.of(pickupAt)).getOrDefault(pickupAt,
                List.of());
        PickupExtraSlot latest = extras.isEmpty() ? null : extras.get(extras.size() - 1);
        Store store = stores.require(storeId);
        history.record(history.event(AutomationAction.EXTRA_SLOT_ADDED, storeId).region(store.getRegionId())
                .secondarySubject(latest == null ? null : latest.getUserId())
                .metadata("storeId", storeId.toString()).metadata("pickupAt", pickupAt.toString())
                .metadata("slotCount", slot.slotCount()).build());
        Instant now = clock.instant();
        boolean mayAddMore = ExtraSlotRule.mayAdd(store.getMaxExtraSlots(), extras.size(), slot.slotCount(),
                maxSlotsPerPickup, true, pickupAt.isAfter(now));
        return new Added(new PickupScheduleService.PickupView(slot, slot.isAvailable(now), null, extras,
                mayAddMore));
    }
}
