package network.foodsharing.region.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.RegionAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionAdminRepository extends JpaRepository<RegionAdmin, UUID> {

    boolean existsByUserIdAndRegionId(UUID userId, UUID regionId);

    boolean existsByUserId(UUID userId);

    Optional<RegionAdmin> findByUserIdAndRegionId(UUID userId, UUID regionId);

    List<RegionAdmin> findByRegionId(UUID regionId);

    @Query("select a.regionId from RegionAdmin a where a.userId = :userId")
    Set<UUID> findRegionIds(@Param("userId") UUID userId);

    @Query("select a.userId from RegionAdmin a where a.regionId = :regionId")
    Set<UUID> findUserIds(@Param("regionId") UUID regionId);
}
