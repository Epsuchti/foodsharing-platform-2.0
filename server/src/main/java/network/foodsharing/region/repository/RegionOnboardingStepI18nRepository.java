package network.foodsharing.region.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.region.domain.RegionOnboardingStepI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionOnboardingStepI18nRepository extends JpaRepository<RegionOnboardingStepI18n, UUID> {

    List<RegionOnboardingStepI18n> findByOwnerIdIn(Collection<UUID> ownerIds);

    void deleteByOwnerId(UUID ownerId);
}
