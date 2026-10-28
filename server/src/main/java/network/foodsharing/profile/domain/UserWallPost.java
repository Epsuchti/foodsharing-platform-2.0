package network.foodsharing.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A post on a profile wall (upstream {@code fs_profile_has_wallpost}, A42); soft-deleted. */
@Getter
@Setter
@Entity
@Table(name = "user_wall_post")
public class UserWallPost extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
