package network.foodsharing.store.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.region.service.RegionStatisticsContributor;
import network.foodsharing.region.service.RegionStoreStats;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * How the store module plugs into others (A21, A22): who may read STORE / STORE_CHAIN history, the public store count
 * and the active cooperations of the region statistics.
 */
@Configuration(proxyBeanMethods = false)
public class StoreIntegrations {

    /** STORE history = store log: maySeeStoreLog (= mayEditStore). */
    @Bean
    HistoryAccessRule storeHistoryAccessRule(StoreRepository stores, StorePermissionService rights) {
        return new HistoryAccessRule() {
            @Override
            public SubjectType subjectType() {
                return SubjectType.STORE;
            }

            @Override
            public boolean canRead(SessionPrincipal actor, UUID subjectId) {
                return actor != null && stores.findById(subjectId)
                        .map(store -> rights.rights(actor, store).maySeeStoreLog()).orElse(false);
            }
        };
    }

    /** STORE_CHAIN history: ORGA, chain administrators (STORE_CHAINS group admins) and the chain's KAMs. */
    @Bean
    HistoryAccessRule storeChainHistoryAccessRule(StoreChainKamRepository kams, PermissionService permissions,
                                                  StoreChainAccess chainAccess) {
        return new HistoryAccessRule() {
            @Override
            public SubjectType subjectType() {
                return SubjectType.STORE_CHAIN;
            }

            @Override
            public boolean canRead(SessionPrincipal actor, UUID subjectId) {
                return actor != null && (chainAccess.mayAdministrateChains(actor)
                        || kams.existsByChainIdAndUserId(subjectId, actor.userId()));
            }
        };
    }

    @Bean
    @Primary
    RegionStoreStats storeRegionStoreStats(StoreRepository stores) {
        return regionId -> (int) stores.countByStatusInRegions(List.of(regionId),
                CooperationStatus.COOPERATION_ESTABLISHED);
    }

    @Bean
    RegionStatisticsContributor storeRegionStatisticsContributor(StoreRepository stores) {
        return (regionId, subtreeIds, statistics) -> statistics.setActiveCooperations((int) stores
                .countByStatusInRegions(subtreeIds, CooperationStatus.COOPERATION_ESTABLISHED));
    }
}
