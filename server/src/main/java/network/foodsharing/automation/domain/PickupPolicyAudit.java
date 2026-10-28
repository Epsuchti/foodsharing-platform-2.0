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

/** One pickup-policy decision (A24). {@code reasons} is JSON {@code [{rule,text}]}. */
@Getter
@Setter
@Entity
@Table(name = "pickup_policy_audit")
public class PickupPolicyAudit extends BaseEntity {

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "pickup_at", nullable = false)
    private Instant pickupAt;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private PickupPolicyDecision decision;

    @Column(name = "reasons", length = 32600)
    private String reasons;

    @Column(name = "user_message", length = 4000)
    private String userMessage;

    @Column(name = "error", length = 2000)
    private String error;
}
