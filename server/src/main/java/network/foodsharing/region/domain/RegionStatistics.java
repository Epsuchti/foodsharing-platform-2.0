package network.foodsharing.region.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Cached region statistics (plan §13.2 R13). */
@Getter
@Setter
@Entity
@Table(name = "region_statistics")
public class RegionStatistics extends BaseEntity {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Column(name = "active_home_region_foodsavers", nullable = false)
    private int activeHomeRegionFoodsavers;

    @Column(name = "active_cooperations", nullable = false)
    private int activeCooperations;

    @Column(name = "pickups_last_month", nullable = false)
    private int pickupsLastMonth;

    @Column(name = "saved_food_weight_last_month", nullable = false)
    private double savedFoodWeightLastMonth;

    @Column(name = "active_food_share_points", nullable = false)
    private int activeFoodSharePoints;

    @Column(name = "food_baskets_last_month", nullable = false)
    private int foodBasketsLastMonth;

    @Column(name = "member_count", nullable = false)
    private int memberCount;
}
