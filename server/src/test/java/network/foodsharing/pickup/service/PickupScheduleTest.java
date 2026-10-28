package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.domain.RegularPickup;
import org.junit.jupiter.api.Test;

/** Occurrence generation (store zone, DST 2026-10-25), overrides, cancellations, sign-ups outside the schedule. */
class PickupScheduleTest {

    static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    static final UUID STORE = UUID.randomUUID();

    static RegularPickup regular(DayOfWeek day, String time, int slots) {
        RegularPickup r = new RegularPickup();
        r.setStoreId(STORE);
        r.setWeekday(day);
        r.setStartTime(LocalTime.parse(time));
        r.setSlotCount(slots);
        return r;
    }

    static PickupDate date(String at, int slots, String description) {
        PickupDate d = new PickupDate();
        d.setStoreId(STORE);
        d.setPickupAt(Instant.parse(at));
        d.setSlotCount(slots);
        d.setDescription(description);
        return d;
    }

    static PickupSignup signup(String at) {
        PickupSignup s = new PickupSignup();
        s.setStoreId(STORE);
        s.setUserId(UUID.randomUUID());
        s.setPickupAt(Instant.parse(at));
        return s;
    }

    @Test
    void localTimeStaysAcrossDstChange() {
        List<PickupSchedule.Slot> slots = PickupSchedule.regularOccurrences(STORE,
                List.of(regular(DayOfWeek.THURSDAY, "19:00", 3)), ZURICH, Instant.parse("2026-10-19T00:00:00Z"),
                Instant.parse("2026-11-02T00:00:00Z"));
        assertThat(slots).extracting(PickupSchedule.Slot::pickupAt).containsExactly(
                Instant.parse("2026-10-22T17:00:00Z"), Instant.parse("2026-10-29T18:00:00Z"));
        assertThat(slots).allMatch(s -> s.regular() && s.slotCount() == 3 && s.signups().isEmpty());
    }

    @Test
    void dstGapMovesForwardAndRangeIsHalfOpen() {
        // 2027-03-28 02:30 does not exist in Zürich → 03:30 CEST = 01:30Z
        List<PickupSchedule.Slot> gap = PickupSchedule.regularOccurrences(STORE,
                List.of(regular(DayOfWeek.SUNDAY, "02:30", 1)), ZURICH, Instant.parse("2027-03-27T00:00:00Z"),
                Instant.parse("2027-03-29T00:00:00Z"));
        assertThat(gap).extracting(PickupSchedule.Slot::pickupAt).containsExactly(Instant.parse("2027-03-28T01:30:00Z"));
        Instant at = Instant.parse("2026-11-02T18:00:00Z");
        assertThat(PickupSchedule.regularOccurrences(STORE, List.of(regular(DayOfWeek.MONDAY, "19:00", 2)), ZURICH,
                at, at.plusMillis(1))).hasSize(1);
        assertThat(PickupSchedule.regularOccurrences(STORE, List.of(regular(DayOfWeek.MONDAY, "19:00", 2)), ZURICH,
                at.minusSeconds(60), at)).isEmpty();
    }

    @Test
    void overridesCancellationsAdditionsAndLaterSignups() {
        Instant from = Instant.parse("2026-11-02T00:00:00Z");
        Instant to = Instant.parse("2026-11-16T00:00:00Z");
        List<PickupSchedule.Slot> regular = PickupSchedule.regularOccurrences(STORE,
                List.of(regular(DayOfWeek.MONDAY, "19:00", 3)), ZURICH, from, to);
        List<PickupSchedule.Slot> merged = PickupSchedule.merge(STORE, regular, List.of(
                date("2026-11-02T18:00:00Z", 5, "mehr Brot"),
                date("2026-11-09T18:00:00Z", 0, null),
                date("2026-11-08T09:00:00Z", 2, "Sonntag")), List.of(
                signup("2026-11-02T18:00:00Z"), signup("2026-11-12T17:00:00Z")));
        assertThat(merged).extracting(PickupSchedule.Slot::pickupAt).containsExactly(
                Instant.parse("2026-11-02T18:00:00Z"), Instant.parse("2026-11-08T09:00:00Z"),
                Instant.parse("2026-11-12T17:00:00Z"));
        assertThat(merged.get(0).slotCount()).isEqualTo(5);
        assertThat(merged.get(0).description()).isEqualTo("mehr Brot");
        assertThat(merged.get(0).regular()).isTrue();
        assertThat(merged.get(0).occupied()).isEqualTo(1);
        assertThat(merged.get(1).regular()).isFalse();
        // a sign-up without occurrence still shows (slot count = its sign-ups)
        assertThat(merged.get(2).slotCount()).isEqualTo(1);
        assertThat(merged.get(2).isAvailable(from)).isFalse();
        assertThat(merged.get(0).isAvailable(from)).isTrue();
        assertThat(merged.get(0).isAvailable(Instant.parse("2026-11-02T18:00:00Z"))).isFalse();
    }
}
