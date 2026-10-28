package network.foodsharing.automation.domain;

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

/** A short-notice sign-out by the user themself; the latest sign-out of a user per pickup wins. */
@Getter
@Setter
@Entity
@Table(name = "signout_cancellation")
public class SignoutCancellation extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;

    @Column(name = "total_slots")
    private Integer totalSlots;

    @Column(name = "occupied_slots")
    private Integer occupiedSlots;

    @Column(name = "canceller_rejoined", nullable = false)
    private boolean cancellerRejoined;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CancellationStatus status = CancellationStatus.OPEN;

    @Column(name = "immediate_notified_at")
    private Instant immediateNotifiedAt;

    @Column(name = "final_checked_at")
    private Instant finalCheckedAt;

    @Column(name = "final_notified_at")
    private Instant finalNotifiedAt;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "message", length = 4000)
    private String message;

    @Column(name = "error", length = 2000)
    private String error;
}
