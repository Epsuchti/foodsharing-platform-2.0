package network.foodsharing.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A report about a member (G1, upstream {@code fs_report}). */
@Getter
@Setter
@Entity
@Table(name = "report")
public class Report extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "reported_user_id", nullable = false)
    private UUID reportedUserId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private ReportReason reason;

    @Column(name = "reason_text", length = 500)
    private String reasonText;

    @Column(name = "message", nullable = false, length = 32600)
    private String message;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "forum_thread_id")
    private UUID forumThreadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReportStatus status = ReportStatus.TO_DO;

    @Column(name = "consequence", length = 4000)
    private String consequence;

    @Column(name = "reminder_at")
    private Instant reminderAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
