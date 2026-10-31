package network.foodsharing.automation.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.RequestDecision;
import network.foodsharing.automation.domain.RequestPolicyAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestPolicyAuditRepository extends JpaRepository<RequestPolicyAudit, UUID> {

    Optional<RequestPolicyAudit> findFirstByStoreIdAndUserIdOrderByCreatedAtDescIdDesc(UUID storeId, UUID userId);

    @Query("select a from RequestPolicyAudit a where a.storeId in :storeIds and a.createdAt >= :from "
            + "and a.createdAt < :to and (:allUsers = true or a.userId = :userId) "
            + "and (:excludeConfirmed = false or a.status not in :confirmed) order by a.createdAt desc, a.id")
    Page<RequestPolicyAudit> search(@Param("storeIds") Collection<UUID> storeIds, @Param("allUsers") boolean allUsers, @Param("userId") UUID userId,
                                    @Param("excludeConfirmed") boolean excludeConfirmed,
                                    @Param("confirmed") Collection<RequestDecision> confirmed,
                                    @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    List<RequestPolicyAudit> findByCreatedAtBefore(java.time.Instant before);

    List<RequestPolicyAudit> findByRegionIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(java.util.Collection<java.util.UUID> regionIds,
            java.time.Instant from, java.time.Instant to);
}
