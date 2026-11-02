package network.foodsharing.fsp.repository;

import java.util.Collection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodSharePointRepository extends JpaRepository<FoodSharePoint, UUID> {

    List<FoodSharePoint> findByRegionIdAndDeletedAtIsNullOrderByNameAscIdAsc(UUID regionId);

    List<FoodSharePoint> findByIdInAndDeletedAtIsNullOrderByNameAscIdAsc(Collection<UUID> ids);

    List<FoodSharePoint> findByStatusAndDeletedAtIsNull(FspStatus status);

    List<FoodSharePoint> findByCreatedByUserId(UUID userId);

    @Query("select count(f) from FoodSharePoint f where f.regionId in :regionIds and f.status = :status "
            + "and f.deletedAt is null")
    long countInRegions(@Param("regionIds") Collection<UUID> regionIds, @Param("status") FspStatus status);

    /** Serialises cleaning sign-ups per Fairteiler (FS2): a row lock on the Fairteiler, as stores do for pickups. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FoodSharePoint f where f.id = :id")
    Optional<FoodSharePoint> lockById(@Param("id") UUID id);

    List<FoodSharePoint> findByRegionIdAndStatusAndCleaningEnabledTrueAndDeletedAtIsNullOrderByNameAscIdAsc(
            UUID regionId, FspStatus status);
}
