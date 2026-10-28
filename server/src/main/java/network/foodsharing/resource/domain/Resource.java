package network.foodsharing.resource.domain;

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

/**
 * A lendable thing (C6, upstream {@code fs_resource}). {@code ownerId} null = commons of {@code regionId}; for a
 * member's resource {@code regionId} optionally restricts it to that region's subtree.
 */
@Getter
@Setter
@Entity
@Table(name = "resource")
public class Resource extends BaseEntity {

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "is_private", nullable = false)
    private boolean privateResource;

    @Enumerated(EnumType.STRING)
    @Column(name = "openness", nullable = false, length = 40)
    private ResourceOpenness openness = ResourceOpenness.ASK_FIRST;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isCommons() {
        return ownerId == null;
    }
}
