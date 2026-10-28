package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicyLinkedStoreMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupPolicyLinkedStoreMemberRepository extends JpaRepository<PickupPolicyLinkedStoreMember, UUID> {

    List<PickupPolicyLinkedStoreMember> findByLinkIdIn(Collection<UUID> linkIds);

    List<PickupPolicyLinkedStoreMember> findByStoreId(UUID storeId);
}
