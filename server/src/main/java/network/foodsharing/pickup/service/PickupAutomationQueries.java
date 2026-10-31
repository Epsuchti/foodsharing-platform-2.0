package network.foodsharing.pickup.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only sign-up queries for the automation module (A24; no permission checks). */
@Service
@Transactional(readOnly = true)
public class PickupAutomationQueries {

    private final PickupSignupRepository signups;
    private final network.foodsharing.pickup.repository.PickupExtraSlotRepository extraSlots;

    public PickupAutomationQueries(PickupSignupRepository signups,
                                   network.foodsharing.pickup.repository.PickupExtraSlotRepository extraSlots) {
        this.signups = signups;
        this.extraSlots = extraSlots;
    }

    /** All sign-ups of the stores with {@code from <= pickupAt < to}. */
    public List<PickupSignup> signups(Collection<UUID> storeIds, Instant from, Instant to) {
        return storeIds.isEmpty() ? List.of() : signups.findInRange(storeIds, from, to);
    }

    /** The user's sign-ups in the stores with {@code from <= pickupAt < to}. */
    public List<PickupSignup> signupsOfUser(UUID userId, Collection<UUID> storeIds, Instant from, Instant to) {
        return storeIds.isEmpty() ? List.of() : signups.findOfUserInStores(userId, storeIds, from, to);
    }

    public Optional<PickupSignup> signup(UUID storeId, UUID userId, Instant pickupAt) {
        return signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt);
    }

    /** Pickup time of the user's latest confirmed sign-up in the store before {@code before}. */
    public Optional<Instant> lastConfirmedPickup(UUID storeId, UUID userId, Instant before) {
        return signups.findFirstByStoreIdAndUserIdAndConfirmedTrueAndPickupAtLessThanOrderByPickupAtDescIdDesc(storeId,
                userId, before).map(PickupSignup::getPickupAt);
    }

    /** Unconfirmed sign-ups after {@code now}, by pickup time. */
    public List<PickupSignup> unconfirmedFuture(Collection<UUID> storeIds, Instant now) {
        return storeIds.isEmpty() ? List.of()
                : signups.findByStoreIdInAndConfirmedFalseAndPickupAtAfterOrderByPickupAtAscIdAsc(storeIds, now);
    }

    /** Extra slots (AU6) added to pickups of these stores with {@code from <= createdAt < to}, newest first. */
    public List<network.foodsharing.pickup.domain.PickupExtraSlot> extraSlotsOfStores(Collection<UUID> storeIds,
                                                                                      Instant from, Instant to) {
        return storeIds.isEmpty() ? List.of() : extraSlots
                .findByStoreIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(storeIds, from, to);
    }

    public List<network.foodsharing.pickup.domain.PickupExtraSlot> extraSlotsInRegions(Collection<UUID> regionIds,
                                                                                       Instant from, Instant to) {
        return regionIds.isEmpty() ? List.of() : extraSlots
                .findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(regionIds, from, to);
    }

    public List<network.foodsharing.pickup.domain.PickupExtraSlot> extraSlotsCreatedBefore(Instant before) {
        return extraSlots.findByCreatedAtBefore(before);
    }

    /** Retention of extra-slot records (the automation archives them as metrics first). */
    @Transactional
    @network.foodsharing.history.NoHistory(reason = "anonymised into automation metrics (AUDITS_ARCHIVED)")
    public void deleteExtraSlots(Collection<network.foodsharing.pickup.domain.PickupExtraSlot> rows) {
        extraSlots.deleteAll(rows);
    }
}
