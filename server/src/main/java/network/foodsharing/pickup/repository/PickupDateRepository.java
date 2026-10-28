package network.foodsharing.pickup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickupDateRepository extends JpaRepository<PickupDate, UUID> {

    Optional<PickupDate> findByStoreIdAndPickupAt(UUID storeId, Instant pickupAt);

    @Query("select p from PickupDate p where p.storeId in :storeIds and p.pickupAt >= :from and p.pickupAt < :to")
    List<PickupDate> findInRange(@Param("storeIds") Collection<UUID> storeIds, @Param("from") Instant from,
                                 @Param("to") Instant to);
}
