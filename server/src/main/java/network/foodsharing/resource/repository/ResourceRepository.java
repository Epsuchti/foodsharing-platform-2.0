package network.foodsharing.resource.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.resource.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    Optional<Resource> findByIdAndDeletedAtIsNull(UUID id);

    List<Resource> findByOwnerIdAndDeletedAtIsNullOrderByName(UUID ownerId);

    long countByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    List<Resource> findByOwnerIdIsNullAndRegionIdAndDeletedAtIsNullOrderByName(UUID regionId);

    /** Members' resources of the given owners, unrestricted or restricted to one of the regions. */
    @Query("select r from Resource r where r.deletedAt is null and r.ownerId in :owners"
            + " and (r.regionId is null or r.regionId in :regions) order by r.name")
    List<Resource> findOwnedIn(@Param("owners") Collection<UUID> owners, @Param("regions") Collection<UUID> regions);

    List<Resource> findByIdInAndDeletedAtIsNullOrderByName(Collection<UUID> ids);

    List<Resource> findByOwnerId(UUID ownerId);
}
