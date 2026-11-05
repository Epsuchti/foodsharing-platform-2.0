package network.foodsharing.intropickup.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** No-op {@link GuideStorePickups} until the pickup module provides one (same pattern as {@code StoreDefaults}). */
@Configuration(proxyBeanMethods = false)
public class IntroPickupDefaults {

    @Bean
    GuideStorePickups noGuideStorePickups() {
        return new GuideStorePickups() {
            @Override
            public List<OwnPickup> myUpcoming() {
                return List.of();
            }

            @Override
            public boolean hasConfirmedSignup(UUID userId, UUID storeId, Instant pickupAt) {
                return false;
            }
        };
    }
}
