package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Anonymous daily count of one metric key in one region (archive of deleted audits, AU7). */
@Getter
@Setter
@Entity
@Table(name = "metric_daily_count")
public class MetricDailyCount extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "metric_day", nullable = false)
    private LocalDate metricDay;

    @Column(name = "metric_key", nullable = false, length = 80)
    private String metricKey;

    @Column(name = "metric_value", nullable = false)
    private long metricValue;
}
