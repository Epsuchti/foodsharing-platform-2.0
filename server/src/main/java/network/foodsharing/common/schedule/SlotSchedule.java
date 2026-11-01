package network.foodsharing.common.schedule;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Pure schedule arithmetic shared by store pickups (A20) and Fairteiler cleaning shifts (FS2): regular weekly
 * occurrences in a zone, one-time rows (override or addition; slot count 0 cancels) and sign-ups outside the schedule.
 * Generic over the sign-up type, so each module keeps its own entities. No I/O.
 */
public final class SlotSchedule {

    /** One row of a weekly schedule; local time in the schedule's zone. */
    public record Weekly(DayOfWeek weekday, LocalTime startTime, int slotCount, String description) {
    }

    /** A one-time slot or the override of a regular occurrence. */
    public record OneTime(Instant at, int slotCount, String description) {
    }

    /** One slot as shown in lists. {@code signups} is never null. */
    public record Slot<S>(Instant at, int slotCount, String description, boolean regular, List<S> signups) {

        public int occupied() {
            return signups.size();
        }

        public boolean isAvailable(Instant now) {
            return occupied() < slotCount && at.isAfter(now);
        }
    }

    private SlotSchedule() {
    }

    /**
     * Occurrences of the weekly schedule with {@code from <= at < to}. Local times that fall into a DST gap move
     * forward (java.time rule); in an overlap the earlier offset is used.
     */
    public static <S> List<Slot<S>> regularOccurrences(Collection<Weekly> regular, ZoneId zone, Instant from,
                                                       Instant to) {
        List<Slot<S>> result = new ArrayList<>();
        if (regular.isEmpty() || !from.isBefore(to)) {
            return result;
        }
        LocalDate day = from.atZone(zone).toLocalDate();
        LocalDate last = to.atZone(zone).toLocalDate();
        for (; !day.isAfter(last); day = day.plusDays(1)) {
            for (Weekly r : regular) {
                if (r.weekday() != day.getDayOfWeek()) {
                    continue;
                }
                Instant at = ZonedDateTime.of(day, r.startTime(), zone).toInstant();
                if (!at.isBefore(from) && at.isBefore(to)) {
                    result.add(new Slot<>(at, r.slotCount(), r.description(), true, List.of()));
                }
            }
        }
        result.sort(Comparator.comparing(Slot::at));
        return result;
    }

    /**
     * Merges regular occurrences, one-time rows and sign-ups. Sign-ups at times without an occurrence still show up
     * (slot count = number of sign-ups); sign-ups are ordered by {@code signedUpAt} (nulls last). Cancelled slots
     * without sign-ups are dropped. Sorted by time.
     */
    public static <S> List<Slot<S>> merge(List<Slot<S>> regular, Collection<OneTime> oneTime, Collection<S> signups,
                                          Function<S, Instant> at, Function<S, Instant> signedUpAt) {
        Map<Instant, Slot<S>> byTime = new TreeMap<>();
        for (Slot<S> slot : regular) {
            byTime.put(slot.at(), slot);
        }
        for (OneTime row : oneTime) {
            Slot<S> previous = byTime.get(row.at());
            byTime.put(row.at(), new Slot<>(row.at(), row.slotCount(), row.description(),
                    previous != null && previous.regular(), List.of()));
        }
        Map<Instant, List<S>> signupsByTime = new TreeMap<>();
        for (S signup : signups) {
            signupsByTime.computeIfAbsent(at.apply(signup), k -> new ArrayList<>()).add(signup);
        }
        for (Map.Entry<Instant, List<S>> e : signupsByTime.entrySet()) {
            List<S> list = e.getValue();
            list.sort(Comparator.comparing(signedUpAt, Comparator.nullsLast(Comparator.naturalOrder())));
            Slot<S> slot = byTime.get(e.getKey());
            byTime.put(e.getKey(), slot == null
                    ? new Slot<>(e.getKey(), list.size(), null, false, List.copyOf(list))
                    : new Slot<>(e.getKey(), Math.max(slot.slotCount(), list.size()), slot.description(),
                            slot.regular(), List.copyOf(list)));
        }
        return byTime.values().stream().filter(s -> s.slotCount() > 0 || !s.signups().isEmpty()).toList();
    }
}
