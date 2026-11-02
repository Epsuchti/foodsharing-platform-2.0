package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * Shared gap list (AU1, decisions 151 and 152): a pending sign-up in one store of the pickup policies with this
 * {@code gap_group_id} is also checked against the person's pickups in the others, with this row's group gap. Each
 * policy keeps its own gap and gap rule switch for its own pickups.
 */
@Getter
@Setter
@Entity
@Table(name = "pickup_gap_group")
public class PickupGapGroup extends BaseEntity {

    @Column(name = "minimum_gap_days", nullable = false)
    private int minimumGapDays;
}
