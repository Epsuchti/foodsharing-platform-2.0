package network.foodsharing.region.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.region.domain.RegionOnboardingStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionOnboardingStepRepository extends JpaRepository<RegionOnboardingStep, UUID> {

    List<RegionOnboardingStep> findByRegionIdOrderBySortOrderAscIdAsc(UUID regionId);
}
