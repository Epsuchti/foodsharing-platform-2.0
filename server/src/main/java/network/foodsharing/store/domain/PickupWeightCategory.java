package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Weight category with an average kg per pickup. */
@Getter
@Setter
@Entity
@Table(name = "pickup_weight_category")
public class PickupWeightCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "min_kg", precision = 6, scale = 2)
    private BigDecimal minKg;

    @Column(name = "max_kg", precision = 6, scale = 2)
    private BigDecimal maxKg;

    @Column(name = "average_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal averageKg;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
