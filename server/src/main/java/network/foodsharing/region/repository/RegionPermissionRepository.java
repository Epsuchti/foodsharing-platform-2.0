package network.foodsharing.region.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.RegionPermission;
import network.foodsharing.security.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionPermissionRepository extends JpaRepository<RegionPermission, UUID> {

    boolean existsByUserIdAndRegionIdAndPermission(UUID userId, UUID regionId, Permission permission);

    Optional<RegionPermission> findByUserIdAndRegionIdAndPermission(UUID userId, UUID regionId, Permission permission);

    List<RegionPermission> findByUserId(UUID userId);

    List<RegionPermission> findByRegionId(UUID regionId);
}
