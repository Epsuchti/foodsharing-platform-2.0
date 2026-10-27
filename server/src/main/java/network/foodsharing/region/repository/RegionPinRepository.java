package network.foodsharing.region.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.RegionPin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionPinRepository extends JpaRepository<RegionPin, UUID> {

    Optional<RegionPin> findByRegionId(UUID regionId);
}
