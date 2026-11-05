package network.foodsharing.automation.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupLinkPending;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupLinkPendingRepository extends JpaRepository<PickupLinkPending, UUID> {

    Optional<PickupLinkPending> findByStoreIdAndPickupAtAndUserId(UUID storeId, Instant pickupAt, UUID userId);

    List<PickupLinkPending> findByStoreIdAndUserId(UUID storeId, UUID userId);

    List<PickupLinkPending> findByUserId(UUID userId);

    /** The store's waiting sign-ups with {@code from <= pickupAt < to} (pickup list). */
    List<PickupLinkPending> findByStoreIdAndPickupAtGreaterThanEqualAndPickupAtLessThan(UUID storeId, Instant from,
                                                                                       Instant to);

    List<PickupLinkPending> findByDeadlineAtLessThanEqualOrderByDeadlineAtAscIdAsc(Instant deadline);

    List<PickupLinkPending> findByPickupAtLessThan(Instant before);
}
