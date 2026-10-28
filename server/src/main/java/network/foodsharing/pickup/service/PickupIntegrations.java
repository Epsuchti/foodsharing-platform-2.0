package network.foodsharing.pickup.service;

import java.time.Clock;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.verification.service.UnverifyGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** How the pickup module plugs into others (A22): unverify guard and PICKUP history access. */
@Configuration(proxyBeanMethods = false)
public class PickupIntegrations {

    /** A user with future sign-ups stays verified (409 HAS_FUTURE_PICKUPS). */
    @Bean
    UnverifyGuard pickupUnverifyGuard(PickupSignupRepository signups, Clock clock) {
        return userId -> {
            if (signups.existsByUserIdAndPickupAtAfter(userId, clock.instant())) {
                throw ApiException.conflict(ApiErrorCode.HAS_FUTURE_PICKUPS);
            }
        };
    }

    /** PICKUP history (subject = store id): the same readers as the store log (maySeeStoreLog). */
    @Bean
    HistoryAccessRule pickupHistoryAccessRule(StoreService stores, StorePermissionService rights) {
        return new HistoryAccessRule() {
            @Override
            public SubjectType subjectType() {
                return SubjectType.PICKUP;
            }

            @Override
            public boolean canRead(SessionPrincipal actor, UUID subjectId) {
                return actor != null && stores.liveStores(java.util.List.of(subjectId)).stream()
                        .anyMatch(store -> rights.rights(actor, store).maySeeStoreLog());
            }
        };
    }
}
