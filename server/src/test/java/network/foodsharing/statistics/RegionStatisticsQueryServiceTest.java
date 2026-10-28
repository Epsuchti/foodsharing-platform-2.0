package network.foodsharing.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import network.foodsharing.statistics.repository.StatisticsReadModel.PickupRow;
import network.foodsharing.statistics.service.RegionStatisticsQueryService;
import network.foodsharing.statistics.service.RegionStatisticsQueryService.Count;
import network.foodsharing.statistics.service.RegionStatisticsQueryService.Month;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** Upstream age bands (ageBandDistrict) and the monthly pickup buckets. */
class RegionStatisticsQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void ageBandsFollowUpstreamEightYearSteps() {
        assertThat(RegionStatisticsQueryService.band(null, TODAY)).isEqualTo("invalid");
        assertThat(RegionStatisticsQueryService.band(TODAY.plusDays(1), TODAY)).isEqualTo("invalid");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(130), TODAY)).isEqualTo("invalid");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(17), TODAY)).isEqualTo("<18");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(18), TODAY)).isEqualTo("18-25");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(25), TODAY)).isEqualTo("18-25");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(26), TODAY)).isEqualTo("26-33");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(41), TODAY)).isEqualTo("34-41");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(65), TODAY)).isEqualTo("58-65");
        assertThat(RegionStatisticsQueryService.band(TODAY.minusYears(66), TODAY)).isEqualTo("66+");
    }

    @Test
    void ageBandListHasEveryBandInOrder() {
        User young = new User();
        young.setDateOfBirth(TODAY.minusYears(20));
        User unknown = new User();
        List<Count> bands = RegionStatisticsQueryService.ageBands(List.of(young, unknown), TODAY);
        assertThat(bands).extracting(Count::label).isEqualTo(RegionStatisticsQueryService.AGE_BANDS);
        assertThat(bands.getFirst().count()).isEqualTo(1);
        assertThat(bands.getLast().count()).isEqualTo(1);
    }

    @Test
    void monthlyBucketsAreZurichMonthsAndIncludeEmptyMonths() {
        ZoneId zurich = ZoneId.of("Europe/Zurich");
        List<PickupRow> rows = List.of(
                new PickupRow(Instant.parse("2026-08-31T22:30:00Z"), 5.0), // 1 Sept in Zurich
                new PickupRow(Instant.parse("2026-09-15T10:00:00Z"), 2.5),
                new PickupRow(Instant.parse("2026-10-01T10:00:00Z"), 1.0));
        List<Month> months = RegionStatisticsQueryService.monthly(rows, YearMonth.of(2026, 7),
                YearMonth.of(2026, 10), zurich);
        assertThat(months).extracting(Month::month).containsExactly("2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(months.get(1).pickups()).isZero();
        assertThat(months.get(2).pickups()).isEqualTo(2);
        assertThat(months.get(2).kg()).isEqualTo(7.5);
        assertThat(months.get(3).kg()).isEqualTo(1.0);
    }
}
