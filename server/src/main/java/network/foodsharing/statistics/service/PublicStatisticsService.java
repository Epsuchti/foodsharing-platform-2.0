package network.foodsharing.statistics.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.statistics.repository.StatisticsDailyRepository;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public platform totals (C3): the latest {@code statistics_daily} row (live totals while none exists yet), kept in
 * memory for {@code statistics.public-cache-minutes}.
 */
@Service
public class PublicStatisticsService {

    public record PublicTotals(double kgSaved, long pickups, long foodsavers, long stores, long cooperations,
                               long foodSharePoints, long baskets, Instant asOf) {
    }

    private record Cached(PublicTotals totals, Instant until) {
    }

    private final StatisticsDailyRepository daily;
    private final StatisticsReadModel readModel;
    private final ConfigurationService configuration;
    private final Clock clock;
    private volatile Cached cached;

    public PublicStatisticsService(StatisticsDailyRepository daily, StatisticsReadModel readModel,
                                   ConfigurationService configuration, Clock clock) {
        this.daily = daily;
        this.readModel = readModel;
        this.configuration = configuration;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PublicTotals current() {
        Instant now = clock.instant();
        Cached snapshot = cached;
        if (snapshot != null && now.isBefore(snapshot.until())) {
            return snapshot.totals();
        }
        PublicTotals totals = daily.findFirstByOrderByDayDesc()
                .map(row -> new PublicTotals(row.getKgSaved(), row.getPickups(), row.getFoodsavers(), row.getStores(),
                        row.getCooperations(), row.getFoodSharePoints(), row.getBaskets(),
                        row.getUpdatedAt() == null ? now : row.getUpdatedAt()))
                .orElseGet(() -> {
                    StatisticsReadModel.Totals live = readModel.totals(now);
                    return new PublicTotals(live.kgSaved(), live.pickups(), live.foodsavers(), live.stores(),
                            live.cooperations(), live.foodSharePoints(), live.baskets(), now);
                });
        int minutes = Math.max(0, configuration.integer(ConfigurationKey.STATISTICS_PUBLIC_CACHE_MINUTES));
        cached = new Cached(totals, now.plus(Duration.ofMinutes(minutes)));
        return totals;
    }

    /** Drops the cache (after an aggregation run). */
    public void evict() {
        cached = null;
    }
}
