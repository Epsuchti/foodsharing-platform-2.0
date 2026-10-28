package network.foodsharing.statistics.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.statistics.domain.StatisticsDaily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticsDailyRepository extends JpaRepository<StatisticsDaily, UUID> {

    Optional<StatisticsDaily> findByDay(LocalDate day);

    Optional<StatisticsDaily> findFirstByOrderByDayDesc();
}
