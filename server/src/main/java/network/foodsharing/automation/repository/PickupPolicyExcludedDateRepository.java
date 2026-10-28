package network.foodsharing.automation.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicyExcludedDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupPolicyExcludedDateRepository extends JpaRepository<PickupPolicyExcludedDate, UUID> {

    List<PickupPolicyExcludedDate> findByPolicyIdOrderByExcludedDate(UUID policyId);
}
