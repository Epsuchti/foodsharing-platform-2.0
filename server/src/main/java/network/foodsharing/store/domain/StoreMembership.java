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

/** A user in a store team (or applied / invited). */
@Getter
@Setter
@Entity
@Table(name = "store_membership")
public class StoreMembership extends BaseEntity {

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StoreMembershipStatus status;

    @Column(name = "manager", nullable = false)
    private boolean manager;

    @Column(name = "application_text", length = 4000)
    private String applicationText;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "added_at")
    private Instant addedAt;
}
