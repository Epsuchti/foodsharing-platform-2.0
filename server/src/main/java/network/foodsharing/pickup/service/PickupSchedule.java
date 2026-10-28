package network.foodsharing.pickup.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
     * forward (java.time rule); in an overlap the earlier offset is used.
     */
    public static List<Slot> regularOccurrences(UUID storeId, Collection<RegularPickup> regular, ZoneId zone,
                                                Instant from, Instant to) {
        List<Slot> result = new ArrayList<>();
        if (regular.isEmpty() || !from.isBefore(to)) {
            return result;
        }
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (; !day.isAfter(last); day = day.plusDays(1)) {
            for (RegularPickup r : regular) {
                if (r.getWeekday() != day.getDayOfWeek()) {
                    continue;
                }
                Instant at = ZonedDateTime.of(day, r.getStartTime(), zone).toInstant();
                if (!at.isBefore(from) && at.isBefore(to)) {
                    result.add(new Slot(storeId, at, r.getSlotCount(), r.getDescription(), true, List.of()));
                }
            }
        }
        result.sort(Comparator.comparing(Slot::pickupAt));
        return result;
    }

    /**
     * Merges regular occurrences, one-time rows (override or addition; slot count 0 cancels) and sign-ups. Sign-ups at
     * times without an occurrence still show up (slot count = number of sign-ups). Cancelled pickups without sign-ups
     * are dropped. Sorted by time.
     */
    public static List<Slot> merge(UUID storeId, List<Slot> regular, Collection<PickupDate> dates,
                                   Collection<PickupSignup> signups) {
        Map<Instant, Slot> byTime = new TreeMap<>();
        for (Slot slot : regular) {
            byTime.put(slot.pickupAt(), slot);
        }
        for (PickupDate date : dates) {
            Slot previous = byTime.get(date.getPickupAt());
            byTime.put(date.getPickupAt(), new Slot(storeId, date.getPickupAt(), date.getSlotCount(),
                    date.getDescription(), previous != null && previous.regular(), List.of()));
        }
        Map<Instant, List<PickupSignup>> signupsByTime = new TreeMap<>();
        for (PickupSignup signup : signups) {
            signupsByTime.computeIfAbsent(signup.getPickupAt(), k -> new ArrayList<>()).add(signup);
        }
        for (Map.Entry<Instant, List<PickupSignup>> e : signupsByTime.entrySet()) {
            List<PickupSignup> list = e.getValue();
            list.sort(Comparator.comparing(PickupSignup::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            Slot slot = byTime.get(e.getKey());
            byTime.put(e.getKey(), slot == null
                    ? new Slot(storeId, e.getKey(), list.size(), null, false, List.copyOf(list))
                    : new Slot(storeId, e.getKey(), Math.max(slot.slotCount(), list.size()), slot.description(),
                            slot.regular(), List.copyOf(list)));
        }
        return byTime.values().stream().filter(s -> s.slotCount() > 0 || !s.signups().isEmpty()).toList();
    }
}
