package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Membership of a user in a region or working group. */
@Getter
@Setter
@Entity
@Table(name = "region_membership")
public class RegionMembership extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "application_text", length = 2000)
    private String applicationText;
}
