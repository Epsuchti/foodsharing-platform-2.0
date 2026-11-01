package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * Shared gap list (AU1, decision 151): the pickup policies with this {@code gap_group_id} share the minimum gap and the
 * gap rule switch, and a pending sign-up in one store is also checked against the person's pickups in the others.
 */
@Getter
@Setter
@Entity
@Table(name = "pickup_gap_group")
public class PickupGapGroup extends BaseEntity {

    @Column(name = "minimum_gap_days", nullable = false)
    private int minimumGapDays;
}
