package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.RequestPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestPolicyRepository extends JpaRepository<RequestPolicy, UUID> {

    Optional<RequestPolicy> findByStoreId(UUID storeId);

    List<RequestPolicy> findByStoreIdIn(Collection<UUID> storeIds);

    List<RequestPolicy> findByEnabledTrue();
}
