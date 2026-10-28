package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One request-policy decision (A24). {@code reason} is the rule code (PolicyRule name). */
@Getter
@Setter
@Entity
@Table(name = "request_policy_audit")
public class RequestPolicyAudit extends BaseEntity {

    @Column(name = "policy_id")
    private UUID policyId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestDecision status;

    @Column(name = "reason", length = 2000)
    private String reason;

    @Column(name = "message", length = 4000)
    private String message;

    @Column(name = "error", length = 2000)
    private String error;
}
