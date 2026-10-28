package network.foodsharing.fsp.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodSharePointRepository extends JpaRepository<FoodSharePoint, UUID> {

    List<FoodSharePoint> findByRegionIdAndDeletedAtIsNullOrderByNameAsc(UUID regionId);

    List<FoodSharePoint> findByIdInAndDeletedAtIsNullOrderByNameAsc(Collection<UUID> ids);

    List<FoodSharePoint> findByStatusAndDeletedAtIsNull(FspStatus status);

    List<FoodSharePoint> findByCreatedByUserId(UUID userId);

    @Query("select count(f) from FoodSharePoint f where f.regionId in :regionIds and f.status = :status "
            + "and f.deletedAt is null")
    long countInRegions(@Param("regionIds") Collection<UUID> regionIds, @Param("status") FspStatus status);
}
