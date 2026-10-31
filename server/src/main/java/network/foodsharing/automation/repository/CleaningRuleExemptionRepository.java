package network.foodsharing.automation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.CleaningRuleExemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CleaningRuleExemptionRepository extends JpaRepository<CleaningRuleExemption, UUID> {

    List<CleaningRuleExemption> findByRegionIdOrderByCreatedAtAscIdAsc(UUID regionId);

    boolean existsByRegionIdAndUserId(UUID regionId, UUID userId);

    Optional<CleaningRuleExemption> findByIdAndRegionId(UUID id, UUID regionId);
}
