package network.foodsharing.basket.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.RegionStatisticsContributor;
import org.springframework.stereotype.Component;

/**
 * Region statistics (R13): baskets created in the previous calendar month (Europe/Zurich) by members of the region
 * subtree. Counted with SQL over {@code basket} and {@code region_membership}.
 */
@Component
public class BasketRegionStatistics implements RegionStatisticsContributor {

    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private final EntityManager em;
    private final Clock clock;

    public BasketRegionStatistics(EntityManager em, Clock clock) {
        this.em = em;
        this.clock = clock;
    }

    @Override
    public void contribute(UUID regionId, Set<UUID> subtreeIds, RegionStatistics statistics) {
        if (subtreeIds.isEmpty()) {
            statistics.setFoodBasketsLastMonth(0);
            return;
        }
        LocalDate firstOfMonth = clock.instant().atZone(ZONE).toLocalDate().with(TemporalAdjusters.firstDayOfMonth());
        Number count = (Number) em.createNativeQuery("select count(*) from basket b where b.created_at >= ?1 "
                        + "and b.created_at < ?2 and exists (select 1 from region_membership m "
                        + "where m.user_id = b.owner_id and m.region_id in (?3))")
                .setParameter(1, firstOfMonth.minusMonths(1).atStartOfDay(ZONE).toOffsetDateTime())
                .setParameter(2, firstOfMonth.atStartOfDay(ZONE).toOffsetDateTime())
                .setParameter(3, subtreeIds).getSingleResult();
        statistics.setFoodBasketsLastMonth(count.intValue());
    }
}
