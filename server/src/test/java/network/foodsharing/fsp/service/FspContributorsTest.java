package network.foodsharing.fsp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.region.domain.RegionStatistics;
import org.junit.jupiter.api.Test;

/** Decision 154: the cleaning numbers of the region statistics and the profile. */
class FspContributorsTest {

    static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    FoodSharePointRepository fsps = mock(FoodSharePointRepository.class);
    FspCleaningSignupRepository signups = mock(FspCleaningSignupRepository.class);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void regionCountsConfirmedShiftsOfTheLastFullMonthInZurichTime() {
        Set<UUID> regions = Set.of(UUID.randomUUID());
        when(fsps.countInRegions(regions, FspStatus.ACTIVE)).thenReturn(2L);
        when(signups.countConfirmedInRegions(regions, Instant.parse("2026-09-30T22:00:00Z"),
                Instant.parse("2026-10-31T23:00:00Z"))).thenReturn(7L);
        RegionStatistics statistics = new RegionStatistics();

        new FspContributors.Statistics(fsps, signups, clock).contribute(UUID.randomUUID(), regions, statistics);

        assertThat(statistics.getCleaningShiftsLastMonth()).isEqualTo(7);
        assertThat(statistics.getActiveFoodSharePoints()).isEqualTo(2);
    }

    @Test
    void regionWithoutSubtreeCountsNothing() {
        RegionStatistics statistics = new RegionStatistics();
        new FspContributors.Statistics(fsps, signups, clock).contribute(UUID.randomUUID(), Set.of(), statistics);
        assertThat(statistics.getCleaningShiftsLastMonth()).isZero();
    }

    @Test
    void profileCountsOnlyPastConfirmedShifts() {
        UUID user = UUID.randomUUID();
        when(signups.countByUserIdAndConfirmedTrueAndShiftAtLessThan(user, NOW)).thenReturn(3L);
        assertThat(new FspContributors.MemberStats(signups, clock).cleaningShiftCount(user)).isEqualTo(3);
    }
}
