package network.foodsharing.statistics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Platform totals of one Zurich day (C3), written by the {@code statistics-aggregation} job. */
@Getter
@Setter
@Entity
@Table(name = "statistics_daily")
public class StatisticsDaily extends BaseEntity {

    @Column(name = "stat_day", nullable = false, unique = true)
    private LocalDate day;

    @Column(name = "kg_saved", nullable = false)
    private double kgSaved;

    @Column(name = "pickups", nullable = false)
    private long pickups;

    @Column(name = "foodsavers", nullable = false)
    private long foodsavers;

    @Column(name = "stores", nullable = false)
    private long stores;

    @Column(name = "cooperations", nullable = false)
    private long cooperations;

    @Column(name = "food_share_points", nullable = false)
    private long foodSharePoints;

    @Column(name = "baskets", nullable = false)
    private long baskets;
}
