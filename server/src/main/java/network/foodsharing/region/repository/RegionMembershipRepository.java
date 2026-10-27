package network.foodsharing.region.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.domain.RegionMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("select m.userId from RegionMembership m where m.regionId = :regionId and m.active = true")
    List<UUID> findActiveUserIds(@Param("regionId") UUID regionId);

    @Query("select distinct m.userId from RegionMembership m where m.regionId in :regionIds and m.active = true")
    List<UUID> findActiveUserIdsIn(@Param("regionIds") Collection<UUID> regionIds);

    /** Active members of a region filtered by user fields (the user table is joined by id, read only). */
    @Query(value = "select m from RegionMembership m, User u where u.id = m.userId and m.regionId = :regionId "
            + "and m.active = true and u.active = true "
            + "and (:search is null or lower(u.displayName) like :search or lower(u.firstName) like :search "
            + "or lower(u.lastName) like :search or lower(u.email) like :search) "
            + "and (:verified is null or u.verified = :verified) "
            + "and (:role is null or u.role = :role) "
            + "and (:admin is null or (:admin = true and exists (select a.id from RegionAdmin a "
            + "where a.regionId = m.regionId and a.userId = m.userId)) or (:admin = false and not exists "
            + "(select a.id from RegionAdmin a where a.regionId = m.regionId and a.userId = m.userId))) "
            + "order by lower(u.displayName), u.id",
            countQuery = "select count(m) from RegionMembership m, User u where u.id = m.userId "
            + "and m.regionId = :regionId and m.active = true and u.active = true "
            + "and (:search is null or lower(u.displayName) like :search or lower(u.firstName) like :search "
            + "or lower(u.lastName) like :search or lower(u.email) like :search) "
            + "and (:verified is null or u.verified = :verified) "
            + "and (:role is null or u.role = :role) "
            + "and (:admin is null or (:admin = true and exists (select a.id from RegionAdmin a "
            + "where a.regionId = m.regionId and a.userId = m.userId)) or (:admin = false and not exists "
            + "(select a.id from RegionAdmin a where a.regionId = m.regionId and a.userId = m.userId)))")
    Page<RegionMembership> searchMembers(@Param("regionId") UUID regionId, @Param("search") String search,
                                         @Param("verified") Boolean verified, @Param("role") Role role,
                                         @Param("admin") Boolean admin, Pageable pageable);
}
