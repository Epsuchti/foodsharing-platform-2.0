package network.foodsharing.region.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op defaults of the store / pickup SPIs (A22) until the store and pickup modules provide them. */
@Configuration(proxyBeanMethods = false)
public class StoreStatsDefaults {

    @Bean
    RegionStoreStats noRegionStoreStats() {
        return regionId -> 0;
    }

    @Bean
    MemberPickupStats noMemberPickupStats() {
        return new MemberPickupStats() {
            @Override
            public Optional<Instant> lastPickupAt(UUID userId) {
                return Optional.empty();
            }

            @Override
            public int pickupCount(UUID userId) {
                return 0;
            }
        };
    }
}
