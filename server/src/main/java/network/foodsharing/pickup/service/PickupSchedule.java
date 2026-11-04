package network.foodsharing.pickup.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.schedule.SlotSchedule;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.domain.RegularPickup;

/**
 * Pure schedule arithmetic (A20): regular occurrences in the store's zone, one-time overrides, sign-ups outside the
 * schedule. No I/O, so the DST and override rules are unit-tested directly.
 */
public final class PickupSchedule {

    /** One pickup of a store as shown in lists. {@code signups} is never null. */
    public record Slot(UUID storeId, Instant pickupAt, int slotCount, String description, boolean regular,
                       List<PickupSignup> signups) {

        public int occupied() {
            return signups.size();
        }

        public boolean isAvailable(Instant now) {
            return occupied() < slotCount && pickupAt.isAfter(now);
        }

        public boolean hasSignup(UUID userId) {
            return signups.stream().anyMatch(s -> s.getUserId().equals(userId));
        }
    }

    private PickupSchedule() {
    }

    /**
     * Occurrences of the weekly schedule with {@code from <= pickupAt < to}. Local times that fall into a DST gap move
     * forward (java.time rule); in an overlap the earlier offset is used. Delegates to {@link SlotSchedule}.
     */
    public static List<Slot> regularOccurrences(UUID storeId, Collection<RegularPickup> regular, ZoneId zone,
                                                Instant from, Instant to) {
        List<SlotSchedule.Weekly> weekly = regular.stream().map(r -> new SlotSchedule.Weekly(r.getWeekday(),
                r.getStartTime(), r.getSlotCount(), r.getDescription())).toList();
        return SlotSchedule.<PickupSignup>regularOccurrences(weekly, zone, from, to).stream()
                .map(s -> toSlot(storeId, s)).toList();
    }

    /**
     * Merges regular occurrences, one-time rows (override or addition; slot count 0 cancels) and sign-ups. Sign-ups at
     * times without an occurrence still show up (slot count = number of sign-ups). Cancelled pickups without sign-ups
     * are dropped. Sorted by time. Delegates to {@link SlotSchedule}.
     */
    public static List<Slot> merge(UUID storeId, List<Slot> regular, Collection<PickupDate> dates,
                                   Collection<PickupSignup> signups) {
        List<SlotSchedule.Slot<PickupSignup>> base = regular.stream().map(s -> new SlotSchedule.Slot<>(s.pickupAt(),
                s.slotCount(), s.description(), s.regular(), s.signups())).toList();
        List<SlotSchedule.OneTime> oneTime = dates.stream().map(d -> new SlotSchedule.OneTime(d.getPickupAt(),
                d.getSlotCount(), d.getDescription())).toList();
        return SlotSchedule.merge(base, oneTime, signups, PickupSignup::getPickupAt, PickupSignup::getCreatedAt,
                        PickupSignup::getId)
                .stream().map(s -> toSlot(storeId, s)).toList();
    }

    private static Slot toSlot(UUID storeId, SlotSchedule.Slot<PickupSignup> s) {
        return new Slot(storeId, s.at(), s.slotCount(), s.description(), s.regular(), s.signups());
    }
}
