package network.foodsharing.pickup.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.pickup.domain.RegularPickup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegularPickupRepository extends JpaRepository<RegularPickup, UUID> {

    List<RegularPickup> findByStoreId(UUID storeId);

    List<RegularPickup> findByStoreIdIn(Collection<UUID> storeIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RegularPickup r where r.storeId = :storeId")
    int deleteByStoreId(@Param("storeId") UUID storeId);
}
