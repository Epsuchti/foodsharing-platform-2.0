package network.foodsharing.automation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.CancellationStatus;
import network.foodsharing.automation.domain.SignoutCancellation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignoutCancellationRepository extends JpaRepository<SignoutCancellation, UUID> {

    Optional<SignoutCancellation> findByStoreIdAndPickupAtAndUserId(UUID storeId, Instant pickupAt, UUID userId);

    List<SignoutCancellation> findByStoreIdAndStatusAndPickupAtLessThanEqual(UUID storeId, CancellationStatus status,
                                                                            Instant pickupAt);

    List<SignoutCancellation> findByStoreIdAndPickupAtGreaterThanEqualAndPickupAtLessThanOrderByPickupAtDescIdDesc(
            UUID storeId, Instant from, Instant to);

    @Query("select c from SignoutCancellation c where c.storeId in :storeIds and c.createdAt >= :from "
            + "and c.createdAt < :to and (:allUsers = true or c.userId = :userId) "
            + "and (:excludeConfirmed = false or c.status not in :confirmed) order by c.createdAt desc, c.id")
    Page<SignoutCancellation> search(@Param("storeIds") Collection<UUID> storeIds, @Param("allUsers") boolean allUsers,
                                     @Param("userId") UUID userId, @Param("excludeConfirmed") boolean excludeConfirmed,
                                     @Param("confirmed") Collection<CancellationStatus> confirmed,
                                     @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<SignoutCancellation> findByCreatedAtBefore(Instant before);

    List<SignoutCancellation> findByFinalCheckedAtGreaterThanEqualAndFinalCheckedAtLessThan(Instant from, Instant to);

    List<SignoutCancellation> findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.util.Collection<java.util.UUID> regionIds,
            java.time.Instant from, java.time.Instant to);
}
