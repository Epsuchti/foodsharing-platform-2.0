package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A post on a food share point wall (A42). */
@Getter
@Setter
@Entity
@Table(name = "fsp_wall_post")
public class FspWallPost extends BaseEntity {

    @Column(name = "fsp_id", nullable = false)
    private UUID fspId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
