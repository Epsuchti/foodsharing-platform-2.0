package network.foodsharing.store.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op {@link StorePickupInfo} until the pickup module provides one. */
@Configuration(proxyBeanMethods = false)
public class StoreDefaults {

    @Bean
    StorePickupInfo noStorePickupInfo() {
        return new StorePickupInfo() {
            @Override
            public Map<UUID, SlotInfo> slotInfo(Collection<UUID> storeIds) {
                return Map.of();
            }

            @Override
            public Map<UUID, MemberStats> memberStats(UUID storeId) {
                return Map.of();
            }

            @Override
            public int deleteFutureSignups(UUID storeId, UUID userId) {
                return 0;
            }

            @Override
            public boolean hasSignups(UUID storeId) {
                return false;
            }
        };
    }
}
