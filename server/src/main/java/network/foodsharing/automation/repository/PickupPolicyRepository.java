package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupPolicyRepository extends JpaRepository<PickupPolicy, UUID> {

    Optional<PickupPolicy> findByStoreId(UUID storeId);

    List<PickupPolicy> findByStoreIdIn(Collection<UUID> storeIds);

    List<PickupPolicy> findByEnabledTrue();
}
