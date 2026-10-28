package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A user of a region to whom the cleaning rule never applies. */
@Getter
@Setter
@Entity
@Table(name = "cleaning_rule_exemption")
public class CleaningRuleExemption extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reason", length = 500)
    private String reason;
}
