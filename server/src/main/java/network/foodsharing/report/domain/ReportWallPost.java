package network.foodsharing.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A post on the internal wall of a report (A42; report handlers only). */
@Getter
@Setter
@Entity
@Table(name = "report_wall_post")
public class ReportWallPost extends BaseEntity {

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
