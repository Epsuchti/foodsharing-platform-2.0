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

    @Modifying
    @Query("delete from HistoryEntry e where e.subjectType = :subjectType and e.action = :action and e.occurredAt < :before")
    int deleteOlderThan(@Param("subjectType") SubjectType subjectType, @Param("action") String action,
                        @Param("before") Instant before);
}
