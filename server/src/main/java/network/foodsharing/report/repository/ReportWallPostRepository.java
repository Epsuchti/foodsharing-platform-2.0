package network.foodsharing.report.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.report.domain.ReportWallPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportWallPostRepository extends JpaRepository<ReportWallPost, UUID> {

    Page<ReportWallPost> findByReportIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID reportId, Pageable pageable);

    Optional<ReportWallPost> findByIdAndReportIdAndDeletedAtIsNull(UUID id, UUID reportId);
}
