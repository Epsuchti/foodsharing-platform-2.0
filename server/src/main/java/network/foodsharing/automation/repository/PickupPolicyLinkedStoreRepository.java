package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicyLinkedStore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupPolicyLinkedStoreRepository extends JpaRepository<PickupPolicyLinkedStore, UUID> {

    List<PickupPolicyLinkedStore> findByPolicyIdOrderByCreatedAtAscIdAsc(UUID policyId);

    List<PickupPolicyLinkedStore> findByMirrorOfIn(Collection<UUID> sourceIds);
}
