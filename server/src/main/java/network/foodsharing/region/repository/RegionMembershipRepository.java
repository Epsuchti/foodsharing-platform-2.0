package network.foodsharing.region.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.region.domain.RegionMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionMembershipRepository extends JpaRepository<RegionMembership, UUID> {

    Optional<RegionMembership> findByUserIdAndRegionId(UUID userId, UUID regionId);

    boolean existsByUserIdAndRegionIdAndActiveTrue(UUID userId, UUID regionId);

    List<RegionMembership> findByUserIdAndActiveTrue(UUID userId);

    List<RegionMembership> findByRegionIdAndActiveTrue(UUID regionId);

    long countByRegionIdAndActiveTrue(UUID regionId);

    @Query("select m.regionId from RegionMembership m where m.userId = :userId and m.active = true")
    Set<UUID> findActiveRegionIds(@Param("userId") UUID userId);

    @Query("select m from RegionMembership m, Region r where m.regionId = r.id and m.active = true "
            + "and r.type <> network.foodsharing.region.domain.UnitType.WORKING_GROUP")
    List<RegionMembership> findActiveRegionMemberships();
}
