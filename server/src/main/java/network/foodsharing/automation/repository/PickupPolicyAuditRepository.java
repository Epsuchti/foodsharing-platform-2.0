package network.foodsharing.automation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PickupPolicyAuditRepository extends JpaRepository<PickupPolicyAudit, UUID> {

    Optional<PickupPolicyAudit> findFirstByStoreIdAndUserIdAndPickupAtOrderByCreatedAtDesc(UUID storeId, UUID userId,
                                                                                           Instant pickupAt);

    @Query("select a from PickupPolicyAudit a where a.storeId in :storeIds and a.createdAt >= :from "
            + "and a.createdAt < :to and (:allUsers = true or a.userId = :userId) "
            + "and (:excludeConfirmed = false or a.decision not in :confirmed) order by a.createdAt desc, a.id")
    Page<PickupPolicyAudit> search(@Param("storeIds") Collection<UUID> storeIds, @Param("allUsers") boolean allUsers, @Param("userId") UUID userId,
                                   @Param("excludeConfirmed") boolean excludeConfirmed,
                                   @Param("confirmed") Collection<PickupPolicyDecision> confirmed,
                                   @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
