package network.foodsharing.region.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.region.domain.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegionRepository extends JpaRepository<Region, UUID> {

    Optional<Region> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByEmailName(String emailName);

    List<Region> findByParentIdOrderBySortOrderAscNameAsc(UUID parentId);

    List<Region> findByParentIdIsNullOrderBySortOrderAscNameAsc();

    long countByParentId(UUID parentId);

    List<Region> findByIdIn(Collection<UUID> ids);

    /** Ancestors (incl. the region itself) from the root down. */
    @Query(value = "select r.* from region r join region_closure c on c.ancestor_id = r.id where c.region_id = :id "
            + "order by c.depth desc", nativeQuery = true)
    List<Region> findAncestors(@Param("id") UUID id);
}
