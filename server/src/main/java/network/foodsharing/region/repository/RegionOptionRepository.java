package network.foodsharing.region.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.RegionOption;
import network.foodsharing.region.domain.RegionOptionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionOptionRepository extends JpaRepository<RegionOption, UUID> {

    List<RegionOption> findByRegionId(UUID regionId);

    Optional<RegionOption> findByRegionIdAndOptionKey(UUID regionId, RegionOptionKey key);
}
