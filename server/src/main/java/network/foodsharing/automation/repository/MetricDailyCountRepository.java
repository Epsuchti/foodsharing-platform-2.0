package network.foodsharing.automation.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.MetricDailyCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricDailyCountRepository extends JpaRepository<MetricDailyCount, UUID> {

    Optional<MetricDailyCount> findByRegionIdAndMetricDayAndMetricKey(UUID regionId, LocalDate day, String key);

    List<MetricDailyCount> findByRegionIdInAndMetricDayBetween(Collection<UUID> regionIds, LocalDate from,
                                                               LocalDate to);
}
