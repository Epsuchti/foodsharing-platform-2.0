package network.foodsharing.pickup.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupExtraSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupExtraSlotRepository extends JpaRepository<PickupExtraSlot, UUID> {

    Optional<PickupExtraSlot> findByStoreIdAndPickupAt(UUID storeId, Instant pickupAt);

    List<PickupExtraSlot> findByStoreIdAndPickupAtIn(UUID storeId, Collection<Instant> pickupAts);

    List<PickupExtraSlot> findByStoreIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Collection<UUID> storeIds, Instant from, Instant to);

    List<PickupExtraSlot> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    List<PickupExtraSlot> findByCreatedAtBefore(Instant before);

    List<PickupExtraSlot> findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Collection<UUID> regionIds,
            Instant from, Instant to);
}
