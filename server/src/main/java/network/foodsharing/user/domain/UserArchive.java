package network.foodsharing.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Snapshot of a deleted account before anonymisation (A40, upstream {@code fs_foodsaver_archive}). */
@Getter
@Setter
@Entity
@Table(name = "user_archive")
public class UserArchive extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "snapshot_json", nullable = false, length = 32600)
    private String snapshotJson;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "deleted_by")
    private UUID deletedBy;
}
