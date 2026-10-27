package network.foodsharing.intropickup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntroPickupSubscriptionRepository extends JpaRepository<IntroPickupSubscription, UUID> {

    Optional<IntroPickupSubscription> findByUserId(UUID userId);

    Optional<IntroPickupSubscription> findByUnsubscribeToken(String token);

    List<IntroPickupSubscription> findByRegionIdAndActiveTrue(UUID regionId);
}
