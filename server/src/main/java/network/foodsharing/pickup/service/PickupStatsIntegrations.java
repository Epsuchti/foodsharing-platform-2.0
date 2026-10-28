package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.region.service.GroupRequirementContributor;
import network.foodsharing.region.service.MemberPickupStats;
import network.foodsharing.region.service.RegionStatisticsContributor;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Pickup figures for other modules (A22, B5): last pickup / count of region members, the "pickups" group requirement
 * and the region statistics (pickups and saved kg of the last calendar month, Europe/Zurich, in the region subtree).
 * Only confirmed past sign-ups count.
 */
@Configuration(proxyBeanMethods = false)
public class PickupStatsIntegrations {

    @Bean
    @Primary
    MemberPickupStats pickupMemberStats(PickupSignupRepository signups, Clock clock) {
        return new MemberPickupStats() {
            @Override
            public Optional<Instant> lastPickupAt(UUID userId) {
                return signups.findFirstByUserIdAndConfirmedTrueAndPickupAtLessThanOrderByPickupAtDesc(userId,
                        clock.instant()).map(PickupSignup::getPickupAt);
            }

            @Override
            public int pickupCount(UUID userId) {
                return (int) signups.countByUserIdAndConfirmedTrueAndPickupAtLessThan(userId, clock.instant());
            }
        };
    }

    /** Confirmed pickup sign-ups for the profile commitments (A6). */
    @Bean
    network.foodsharing.user.service.CommitmentSource pickupCommitments(PickupSignupRepository signups) {
        return (userId, from, to) -> signups
                .findByUserIdAndPickupAtGreaterThanEqualAndPickupAtLessThanOrderByPickupAt(userId, from, to).stream()
                .filter(PickupSignup::isConfirmed)
                .map(s -> new network.foodsharing.user.service.CommitmentSource.Commitment(
                        network.foodsharing.user.service.CommitmentSource.Kind.PICKUP, s.getPickupAt()))
                .toList();
    }

    @Bean
    GroupRequirementContributor pickupGroupRequirements(MemberPickupStats stats) {
        return new GroupRequirementContributor() {
            @Override
            public int pickups(UUID userId) {
                return stats.pickupCount(userId);
            }
        };
    }

    @Bean
    RegionStatisticsContributor pickupRegionStatistics(PickupSignupRepository signups,
                                                       ObjectProvider<StoreService> storeService, Clock clock) {
        return (regionId, subtreeIds, statistics) -> {
            StoreService stores = storeService.getObject();
            List<Store> inTree = stores.liveStoresInRegions(subtreeIds);
            if (inTree.isEmpty()) {
                statistics.setPickupsLastMonth(0);
                statistics.setSavedFoodWeightLastMonth(0);
                return;
            }
            ZonedDateTime monthStart = clock.instant().atZone(ZoneId.of("Europe/Zurich")).withDayOfMonth(1)
                    .toLocalDate().atStartOfDay(ZoneId.of("Europe/Zurich"));
            Instant from = monthStart.minusMonths(1).toInstant();
            Instant to = monthStart.toInstant();
            Map<UUID, Store> byId = inTree.stream().collect(Collectors.toMap(Store::getId, Function.identity()));
            List<PickupSignup> rows = signups.findConfirmedInStores(byId.keySet(), from, to);
            Map<String, Double> kgByCategory = new java.util.HashMap<>();
            double kg = 0;
            for (PickupSignup row : rows) {
                String category = byId.get(row.getStoreId()).getWeightCategory();
                kg += kgByCategory.computeIfAbsent(String.valueOf(category),
                        c -> stores.averageKg(category));
            }
            statistics.setPickupsLastMonth(rows.size());
            statistics.setSavedFoodWeightLastMonth(Math.round(kg * 10) / 10.0);
        };
    }
}
