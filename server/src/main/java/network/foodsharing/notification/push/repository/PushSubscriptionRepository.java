package network.foodsharing.notification.push.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.push.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from PushSubscription p where p.failureCount >= :max")
    int deleteFailing(@org.springframework.data.repository.query.Param("max") int max);
}
