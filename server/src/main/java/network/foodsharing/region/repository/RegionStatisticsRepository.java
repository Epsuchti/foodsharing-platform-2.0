package network.foodsharing.region.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.RegionStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionStatisticsRepository extends JpaRepository<RegionStatistics, UUID> {

    Optional<RegionStatistics> findByRegionId(UUID regionId);
}
