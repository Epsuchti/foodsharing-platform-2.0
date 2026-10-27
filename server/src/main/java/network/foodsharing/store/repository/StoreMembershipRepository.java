package network.foodsharing.store.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.StoreMembership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreMembershipRepository extends JpaRepository<StoreMembership, UUID> {

    Optional<StoreMembership> findByStoreIdAndUserId(UUID storeId, UUID userId);

    List<StoreMembership> findByStoreId(UUID storeId);

    List<StoreMembership> findByUserId(UUID userId);

    List<StoreMembership> findByStoreIdIn(Collection<UUID> storeIds);
}
