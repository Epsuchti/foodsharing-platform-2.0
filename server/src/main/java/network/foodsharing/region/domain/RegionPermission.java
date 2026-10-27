package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.security.Permission;

/** A region-scoped permission granted directly to a user. */
@Getter
@Setter
@Entity
@Table(name = "region_permission")
public class RegionPermission extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 60)
    private Permission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;
}
