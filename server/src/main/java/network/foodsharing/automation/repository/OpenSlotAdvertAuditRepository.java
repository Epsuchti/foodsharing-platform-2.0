package network.foodsharing.automation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.AdvertStatus;
import network.foodsharing.automation.domain.OpenSlotAdvertAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpenSlotAdvertAuditRepository extends JpaRepository<OpenSlotAdvertAudit, UUID> {

    /** Audits of one (advert, pickup, trigger hours), without the given status, oldest first. */
    @Query("select a from OpenSlotAdvertAudit a where a.advertId = :advertId and a.pickupAt = :pickupAt "
            + "and a.triggerHoursBefore = :hours and a.status <> :excluded order by a.createdAt")
    List<OpenSlotAdvertAudit> findForPickup(@Param("advertId") UUID advertId, @Param("pickupAt") Instant pickupAt,
                                            @Param("hours") Integer triggerHoursBefore,
                                            @Param("excluded") AdvertStatus excluded);

    boolean existsByStoreIdAndPickupAtAndStatusIn(UUID storeId, Instant pickupAt, Collection<AdvertStatus> statuses);

    /** Telegram adverts still visible for this pickup or for pickups that have passed. */
    @Query("select a from OpenSlotAdvertAudit a where a.storeId = :storeId and a.telegramMessageId is not null "
            + "and a.telegramDeletedAt is null and (a.pickupAt = :pickupAt or a.pickupAt < :now)")
    List<OpenSlotAdvertAudit> telegramToDelete(@Param("storeId") UUID storeId, @Param("pickupAt") Instant pickupAt,
                                               @Param("now") Instant now);

    @Query("select a from OpenSlotAdvertAudit a where a.storeId in :storeIds and a.createdAt >= :from "
            + "and a.createdAt < :to and (:allUsers = true or a.userId = :userId) "
            + "and (:excludeConfirmed = false or a.status not in :confirmed) order by a.createdAt desc, a.id")
    Page<OpenSlotAdvertAudit> search(@Param("storeIds") Collection<UUID> storeIds, @Param("allUsers") boolean allUsers,
                                     @Param("userId") UUID userId, @Param("excludeConfirmed") boolean excludeConfirmed,
                                     @Param("confirmed") Collection<AdvertStatus> confirmed,
                                     @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<OpenSlotAdvertAudit> findByCreatedAtBefore(Instant before);

    List<OpenSlotAdvertAudit> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    List<OpenSlotAdvertAudit> findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.util.Collection<java.util.UUID> regionIds,
            java.time.Instant from, java.time.Instant to);
}
