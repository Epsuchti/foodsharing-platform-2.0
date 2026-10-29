package network.foodsharing.history.repository;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoryEntryRepository extends JpaRepository<HistoryEntry, UUID>, JpaSpecificationExecutor<HistoryEntry> {

    /** Distinct subjects of entries with this action code in the region (e.g. guides disabled there). */
    @Query("select distinct e.subjectId from HistoryEntry e where e.subjectType = :subjectType and e.action = :action"
            + " and e.regionId = :regionId")
    java.util.List<UUID> subjectIds(@Param("subjectType") SubjectType subjectType, @Param("action") String action,
                                    @Param("regionId") UUID regionId);

    @Modifying
    @Query("delete from HistoryEntry e where e.subjectType = :subjectType and e.action = :action and e.occurredAt < :before")
    int deleteOlderThan(@Param("subjectType") SubjectType subjectType, @Param("action") String action,
                        @Param("before") Instant before);

    /** Clears the values of an action's entries about a user (account deletion, ACCOUNT_LIFETIME retention). */
    @Modifying
    @Query("update HistoryEntry e set e.oldValue = null, e.newValue = null, e.reason = null, e.metadata = null "
            + "where e.subjectType = :subjectType and e.action = :action "
            + "and (e.subjectId = :userId or e.secondarySubjectId = :userId)")
    int anonymise(@Param("subjectType") SubjectType subjectType, @Param("action") String action,
                  @Param("userId") java.util.UUID userId);
}
