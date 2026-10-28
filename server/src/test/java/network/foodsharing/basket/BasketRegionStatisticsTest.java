package network.foodsharing.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.basket.service.BasketRegionStatistics;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.RegionService;
import org.junit.jupiter.api.Test;

/** R13: baskets of the previous month are counted per basket for owners that are members of the subtree. */
class BasketRegionStatisticsTest {

    private final BasketRepository baskets = mock(BasketRepository.class);
    private final RegionService regions = mock(RegionService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
    private final BasketRegionStatistics contributor = new BasketRegionStatistics(baskets, regions, clock);

    @Test
    void countsBasketsOfMembersInThePreviousMonth() {
        UUID region = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        when(baskets.findOwnerIdsCreatedBetween(Instant.parse("2026-08-31T22:00:00Z"),
                Instant.parse("2026-09-30T22:00:00Z"))).thenReturn(List.of(member, member, stranger));
        when(regions.activeMembersAmong(eq(Set.of(region)), any())).thenReturn(Set.of(member));
        RegionStatistics statistics = new RegionStatistics();
        contributor.contribute(region, Set.of(region), statistics);
        assertThat(statistics.getFoodBasketsLastMonth()).isEqualTo(2);
        verify(regions).activeMembersAmong(eq(Set.of(region)), eq(List.of(member, member, stranger)));
    }

    @Test
    void noBasketsMeansZeroWithoutAskingTheRegionModule() {
        when(baskets.findOwnerIdsCreatedBetween(any(), any())).thenReturn(List.of());
        RegionStatistics statistics = new RegionStatistics();
        statistics.setFoodBasketsLastMonth(5);
        contributor.contribute(UUID.randomUUID(), Set.of(UUID.randomUUID()), statistics);
        assertThat(statistics.getFoodBasketsLastMonth()).isZero();
        verify(regions, never()).activeMembersAmong(any(), any());
    }
}
