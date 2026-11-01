package network.foodsharing.fsp.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.fsp.domain.RegionCleaningFsp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionCleaningFspRepository extends JpaRepository<RegionCleaningFsp, UUID> {

    List<RegionCleaningFsp> findByRegionId(UUID regionId);

    List<RegionCleaningFsp> findByFspId(UUID fspId);
}
