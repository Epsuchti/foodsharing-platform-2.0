package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A date (store time zone) on which the pickup policy never decides (manual check, rule MANUAL_DATE). */
@Getter
@Setter
@Entity
@Table(name = "pickup_policy_excluded_date")
public class PickupPolicyExcludedDate extends BaseEntity {

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "excluded_date", nullable = false)
    private LocalDate excludedDate;
}
