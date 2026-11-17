package network.foodsharing.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.statistics.domain.StatisticsDaily;
import network.foodsharing.statistics.repository.StatisticsDailyRepository;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import network.foodsharing.statistics.service.PublicStatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * C3 public totals cache: kept for {@code statistics.public-cache-minutes} of the injected clock, dropped by an
 * aggregation run and by the dev seed reset (under the fixed e2e clock it would otherwise never expire), and missed
 * when the clock is set back before the cached moment.
 */
class PublicStatisticsServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    StatisticsDailyRepository daily = mock(StatisticsDailyRepository.class);
    StatisticsReadModel readModel = mock(StatisticsReadModel.class);
    ConfigurationService configuration = mock(ConfigurationService.class);
    MutableClock clock = new MutableClock(NOW);
    PublicStatisticsService service = new PublicStatisticsService(daily, readModel, configuration, clock);

    @BeforeEach
    void setUp() {
        when(configuration.integer(ConfigurationKey.STATISTICS_PUBLIC_CACHE_MINUTES)).thenReturn(15);
    }

    @Test
    void keepsTheTotalsForTheConfiguredMinutesOfTheClock() {
        latest(1000);
        assertThat(service.current().kgSaved()).isEqualTo(1000);
        latest(2000);
        clock.now = NOW.plusSeconds(14 * 60);
        assertThat(service.current().kgSaved()).isEqualTo(1000);
        clock.now = NOW.plusSeconds(15 * 60);
        assertThat(service.current().kgSaved()).isEqualTo(2000);
    }

    @Test
    void theSeedResetDropsTheCacheEvenUnderAFixedClock() {
        latest(0);
        assertThat(service.current().kgSaved()).isZero();
        latest(1481927.0);
        assertThat(service.current().kgSaved()).as("cached, the clock does not move").isZero();
        service.rebuildAfterSeedReset();
        assertThat(service.current().kgSaved()).isEqualTo(1481927.0);
    }

    @Test
    void anAggregationRunDropsTheCache() {
        latest(1000);
        service.current();
        latest(3000);
        service.evict();
        assertThat(service.current().kgSaved()).isEqualTo(3000);
    }

    @Test
    void aClockSetBackBeforeTheCachedMomentReadsAgain() {
        latest(1000);
        service.current();
        latest(500);
        clock.now = NOW.minusSeconds(3600);
        assertThat(service.current().kgSaved()).isEqualTo(500);
    }

    @Test
    void withoutADailyRowTheLiveTotalsAreShown() {
        when(daily.findFirstByOrderByDayDescIdDesc()).thenReturn(Optional.empty());
        when(readModel.totals(NOW)).thenReturn(new StatisticsReadModel.Totals(12.5, 7, 5, 25, 20, 6, 8));
        PublicStatisticsService.PublicTotals totals = service.current();
        assertThat(totals.kgSaved()).isEqualTo(12.5);
        assertThat(totals.pickups()).isEqualTo(7);
        assertThat(totals.asOf()).isEqualTo(NOW);
    }

    private void latest(double kg) {
        StatisticsDaily row = new StatisticsDaily();
        row.setDay(LocalDate.of(2026, 11, 1));
        row.setKgSaved(kg);
        when(daily.findFirstByOrderByDayDescIdDesc()).thenReturn(Optional.of(row));
    }

    /** A clock the test moves. */
    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
