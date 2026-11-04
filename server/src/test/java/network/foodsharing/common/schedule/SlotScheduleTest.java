package network.foodsharing.common.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The generic schedule arithmetic shared by pickups and cleaning shifts (zone, DST, overrides, sign-ups). */
class SlotScheduleTest {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    /** A sign-up as the generic code sees it. */
    record Signup(String name, Instant at, Instant signedUpAt) {
    }

    static SlotSchedule.Weekly weekly(DayOfWeek day, String time, int slots) {
        return new SlotSchedule.Weekly(day, LocalTime.parse(time), slots, null);
    }

    static List<SlotSchedule.Slot<Signup>> merge(List<SlotSchedule.Slot<Signup>> regular,
                                                 List<SlotSchedule.OneTime> oneTime, List<Signup> signups) {
        return SlotSchedule.merge(regular, oneTime, signups, Signup::at, Signup::signedUpAt, Signup::name);
    }

    @Test
    void localTimeStaysAcrossDstChange() {
        List<SlotSchedule.Slot<Signup>> slots = SlotSchedule.regularOccurrences(
                List.of(weekly(DayOfWeek.SATURDAY, "10:00", 2)), ZURICH, Instant.parse("2026-10-19T00:00:00Z"),
                Instant.parse("2026-11-02T00:00:00Z"));
        assertThat(slots).extracting(SlotSchedule.Slot::at).containsExactly(
                Instant.parse("2026-10-24T08:00:00Z"), Instant.parse("2026-10-31T09:00:00Z"));
        assertThat(slots).allMatch(s -> s.regular() && s.slotCount() == 2 && s.signups().isEmpty());
    }

    @Test
    void dstGapMovesForwardAndRangeIsHalfOpen() {
        Instant from = Instant.parse("2027-03-28T00:00:00Z");
        List<SlotSchedule.Slot<Signup>> slots = SlotSchedule.regularOccurrences(
                List.of(weekly(DayOfWeek.SUNDAY, "02:30", 1)), ZURICH, from, Instant.parse("2027-03-28T01:30:00Z"));
        assertThat(slots).isEmpty();
        slots = SlotSchedule.regularOccurrences(List.of(weekly(DayOfWeek.SUNDAY, "02:30", 1)), ZURICH, from,
                Instant.parse("2027-03-28T01:30:01Z"));
        assertThat(slots).extracting(SlotSchedule.Slot::at).containsExactly(Instant.parse("2027-03-28T01:30:00Z"));
    }

    @Test
    void emptyScheduleOrRangeGivesNothing() {
        Instant at = Instant.parse("2026-11-02T09:00:00Z");
        assertThat(SlotSchedule.<Signup>regularOccurrences(List.of(), ZURICH, at, at.plusSeconds(86400))).isEmpty();
        assertThat(SlotSchedule.<Signup>regularOccurrences(List.of(weekly(DayOfWeek.MONDAY, "10:00", 1)), ZURICH, at,
                at)).isEmpty();
    }

    @Test
    void oneTimeRowsOverrideAddAndCancel() {
        Instant regularAt = Instant.parse("2026-11-07T09:00:00Z");
        Instant cancelledAt = Instant.parse("2026-11-14T09:00:00Z");
        Instant extraAt = Instant.parse("2026-11-11T17:00:00Z");
        List<SlotSchedule.Slot<Signup>> regular = SlotSchedule.regularOccurrences(
                List.of(weekly(DayOfWeek.SATURDAY, "10:00", 2)), ZURICH, Instant.parse("2026-11-02T00:00:00Z"),
                Instant.parse("2026-11-15T00:00:00Z"));
        List<SlotSchedule.Slot<Signup>> merged = merge(regular, List.of(
                new SlotSchedule.OneTime(regularAt, 3, "Grossputz"), new SlotSchedule.OneTime(cancelledAt, 0, null),
                new SlotSchedule.OneTime(extraAt, 1, "Sonderputz")), List.of());
        assertThat(merged).extracting(SlotSchedule.Slot::at).containsExactly(regularAt, extraAt);
        assertThat(merged.get(0)).matches(s -> s.slotCount() == 3 && s.regular() && "Grossputz".equals(s.description()));
        assertThat(merged.get(1)).matches(s -> s.slotCount() == 1 && !s.regular());
    }

    @Test
    void signupsAreOrderedAndKeepCancelledOrUnscheduledSlotsVisible() {
        Instant cancelledAt = Instant.parse("2026-11-14T09:00:00Z");
        Instant loneAt = Instant.parse("2026-11-20T09:00:00Z");
        Instant now = Instant.parse("2026-11-02T09:00:00Z");
        Signup late = new Signup("late", cancelledAt, now.plusSeconds(60));
        Signup early = new Signup("early", cancelledAt, now);
        Signup undated = new Signup("undated", cancelledAt, null);
        Signup lone = new Signup("lone", loneAt, now);
        List<SlotSchedule.Slot<Signup>> merged = merge(List.of(), List.of(new SlotSchedule.OneTime(cancelledAt, 0,
                null)), List.of(late, undated, lone, early));
        assertThat(merged).extracting(SlotSchedule.Slot::at).containsExactly(cancelledAt, loneAt);
        assertThat(merged.get(0).signups()).extracting(Signup::name).containsExactly("early", "late", "undated");
        assertThat(merged.get(0).slotCount()).isEqualTo(3);
        assertThat(merged.get(1)).matches(s -> s.slotCount() == 1 && !s.regular() && s.description() == null);
    }

    @Test
    void signupsWithTheSameSignUpTimeKeepAStableOrderById() {
        Instant at = Instant.parse("2026-11-14T09:00:00Z");
        Instant signedUpAt = Instant.parse("2026-11-02T09:00:00Z");
        Signup b = new Signup("b", at, signedUpAt);
        Signup a = new Signup("a", at, signedUpAt);
        Signup c = new Signup("c", at, signedUpAt.minusSeconds(1));
        assertThat(merge(List.of(), List.of(), List.of(b, a, c)).get(0).signups()).extracting(Signup::name)
                .containsExactly("c", "a", "b");
        assertThat(merge(List.of(), List.of(), List.of(a, c, b)).get(0).signups()).extracting(Signup::name)
                .containsExactly("c", "a", "b");
    }

    @Test
    void availabilityNeedsAFreePlaceInTheFuture() {
        Instant now = Instant.parse("2026-11-02T09:00:00Z");
        SlotSchedule.Slot<Signup> free = new SlotSchedule.Slot<>(now.plusSeconds(3600), 2,
                null, true, List.of(new Signup("a", now.plusSeconds(3600), now)));
        assertThat(free.occupied()).isEqualTo(1);
        assertThat(free.isAvailable(now)).isTrue();
        assertThat(free.isAvailable(now.plusSeconds(3600))).isFalse();
        SlotSchedule.Slot<Signup> full = new SlotSchedule.Slot<>(now.plusSeconds(3600), 1, null, true,
                free.signups());
        assertThat(full.isAvailable(now)).isFalse();
    }
}
