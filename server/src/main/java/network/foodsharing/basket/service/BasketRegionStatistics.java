package network.foodsharing.basket.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.region.service.RegionStatisticsContributor;
import org.springframework.stereotype.Component;

/**
 * Region statistics (R13): baskets created in the previous calendar month (Europe/Zurich) by active members of the
 * region subtree. Membership is asked from the region module ({@link RegionService#activeMembersAmong}).
 */
@Component
public class BasketRegionStatistics implements RegionStatisticsContributor {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private final BasketRepository baskets;
    private final RegionService regions;
    private final Clock clock;

    public BasketRegionStatistics(BasketRepository baskets, RegionService regions, Clock clock) {
        this.baskets = baskets;
        this.regions = regions;
        this.clock = clock;
    }

    @Override
    public void contribute(UUID regionId, Set<UUID> subtreeIds, RegionStatistics statistics) {
        LocalDate firstOfMonth = clock.instant().atZone(ZONE).toLocalDate().with(TemporalAdjusters.firstDayOfMonth());
        List<UUID> owners = subtreeIds.isEmpty() ? List.of() : baskets.findOwnerIdsCreatedBetween(
                firstOfMonth.minusMonths(1).atStartOfDay(ZONE).toInstant(), firstOfMonth.atStartOfDay(ZONE).toInstant());
        Set<UUID> members = owners.isEmpty() ? Set.of() : regions.activeMembersAmong(subtreeIds, owners);
        statistics.setFoodBasketsLastMonth((int) owners.stream().filter(members::contains).count());
    }
}
