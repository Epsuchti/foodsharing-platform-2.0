package network.foodsharing.store.domain;

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

/** A post or system milestone on a store wall (upstream fs_betrieb_notiz); deleted posts are kept with deleted_at. */
@Getter
@Setter
@Entity
@Table(name = "store_wall_post")
public class StoreWallPost extends BaseEntity {

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone", nullable = false, length = 30)
    private WallMilestone milestone = WallMilestone.NONE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;
}
