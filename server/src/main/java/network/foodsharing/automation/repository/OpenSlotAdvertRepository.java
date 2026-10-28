package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.OpenSlotAdvert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpenSlotAdvertRepository extends JpaRepository<OpenSlotAdvert, UUID> {

    List<OpenSlotAdvert> findByStoreIdOrderByAdvertNumber(UUID storeId);

    List<OpenSlotAdvert> findByStoreIdIn(Collection<UUID> storeIds);

    Optional<OpenSlotAdvert> findByStoreIdAndAdvertNumber(UUID storeId, int advertNumber);

    List<OpenSlotAdvert> findByEnabledTrueOrderByStoreIdAscAdvertNumberAsc();
}
