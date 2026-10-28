package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.store.domain.FreeSlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Weekly schedule validation (B4) and the free-slot traffic light of store lists. */
class RegularPickupServiceTest {

    static final UUID STORE = UUID.randomUUID();

    static ApiErrorCode codeOf(List<RegularPickupService.Entry> entries) {
        try {
            RegularPickupService.validate(STORE, entries, 50);
            return null;
        } catch (ApiException e) {
            return (ApiErrorCode) ReflectionTestUtils.getField(e, "code");
        }
    }

    static RegularPickupService.Entry e(DayOfWeek day, String time, int slots) {
        return new RegularPickupService.Entry(day, time, slots, null);
    }

    @Test
    void validScheduleIsSortedAndTrimmed() {
        var result = RegularPickupService.validate(STORE, List.of(e(DayOfWeek.THURSDAY, "19:00", 3),
                new RegularPickupService.Entry(DayOfWeek.MONDAY, " 18:30 ", 0, "  Hintereingang ")), 50);
        assertThat(result).extracting(r -> r.getWeekday()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
        assertThat(result.getFirst().getStartTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(result.getFirst().getDescription()).isEqualTo("Hintereingang");
        assertThat(RegularPickupService.describe(result)).isEqualTo("MONDAY 18:30 ×0 (Hintereingang); THURSDAY 19:00 ×3");
        assertThat(codeOf(List.of())).isNull();
    }

    @Test
    void rejectsBadEntries() {
        assertThat(codeOf(List.of(e(DayOfWeek.MONDAY, "19:00", 51)))).isEqualTo(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        assertThat(codeOf(List.of(e(DayOfWeek.MONDAY, "19:00", -1)))).isEqualTo(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        assertThat(codeOf(List.of(e(DayOfWeek.MONDAY, "19:00", 2), e(DayOfWeek.MONDAY, "19:00:00", 3))))
                .isEqualTo(ApiErrorCode.DUPLICATE_PICKUP_DAY_TIME);
        assertThat(codeOf(List.of(e(DayOfWeek.MONDAY, "25:00", 2)))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(List.of(e(null, "19:00", 2)))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(codeOf(List.of(new RegularPickupService.Entry(DayOfWeek.MONDAY, "19:00", 2, "x".repeat(101)))))
                .isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void freeSlotTrafficLight() {
        ZoneId zurich = ZoneId.of("Europe/Zurich");
        Instant now = Instant.parse("2026-11-02T22:30:00Z"); // 23:30 Monday in Zürich
        assertThat(StorePickupInfoImpl.freeSlotStatus(null, now, zurich, 5)).isEqualTo(FreeSlotStatus.GREEN);
        assertThat(StorePickupInfoImpl.freeSlotStatus(Instant.parse("2026-11-03T22:00:00Z"), now, zurich, 5))
                .isEqualTo(FreeSlotStatus.RED); // Tuesday 23:00 = tomorrow
        assertThat(StorePickupInfoImpl.freeSlotStatus(Instant.parse("2026-11-03T23:30:00Z"), now, zurich, 5))
                .isEqualTo(FreeSlotStatus.ORANGE); // Wednesday 00:30 = in 2 days
        assertThat(StorePickupInfoImpl.freeSlotStatus(Instant.parse("2026-11-05T18:00:00Z"), now, zurich, 5))
                .isEqualTo(FreeSlotStatus.ORANGE);
        assertThat(StorePickupInfoImpl.freeSlotStatus(Instant.parse("2026-11-07T18:00:00Z"), now, zurich, 5))
                .isEqualTo(FreeSlotStatus.YELLOW);
        assertThat(StorePickupInfoImpl.freeSlotStatus(Instant.parse("2026-11-08T18:00:00Z"), now, zurich, 5))
                .isEqualTo(FreeSlotStatus.GREEN);
    }
}
