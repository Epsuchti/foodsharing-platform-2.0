package network.foodsharing.report.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.report.domain.Report;
import network.foodsharing.report.domain.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByIdAndDeletedAtIsNull(UUID id);

    /** Reports of a region, optionally by status, newest first. */
    @Query("""
            select r from Report r where r.regionId = :regionId and r.deletedAt is null
              and (:status is null or r.status = :status)
            order by r.createdAt desc, r.id""")
    Page<Report> list(@Param("regionId") UUID regionId, @Param("status") ReportStatus status, Pageable pageable);

    List<Report> findByReportedUserIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID reportedUserId);

    /** Due reminders of open reports (ReportReminderJob). */
    @Query("""
            select r from Report r where r.deletedAt is null and r.reminderAt is not null and r.reminderAt <= :now
              and r.status <> :completed""")
    List<Report> dueReminders(@Param("now") Instant now, @Param("completed") ReportStatus completed);

    List<Report> findByReporterIdOrReportedUserId(UUID reporterId, UUID reportedUserId);

    long countByRegionIdInAndDeletedAtIsNull(Collection<UUID> regionIds);
}
