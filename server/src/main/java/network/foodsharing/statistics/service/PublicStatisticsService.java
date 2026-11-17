package network.foodsharing.statistics.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import network.foodsharing.admin.domain.ConfigurationKey;
import network.foodsharing.admin.service.ConfigurationService;
import network.foodsharing.common.seed.SeedDerivedData;
import network.foodsharing.statistics.repository.StatisticsDailyRepository;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public platform totals (C3): the latest {@code statistics_daily} row (live totals while none exists yet), kept in
 * memory for {@code statistics.public-cache-minutes} of the injected {@link Clock}. The cache is dropped after an
 * aggregation run and after the dev seed reset ({@link SeedDerivedData}), whose restored rows it would otherwise hide
 * for good under the fixed e2e clock; a clock set back before the cached moment (dev time travel) also misses it.
 */
@Service
public class PublicStatisticsService implements SeedDerivedData {

    public record PublicTotals(double kgSaved, long pickups, long foodsavers, long stores, long cooperations,
                               long foodSharePoints, long baskets, Instant asOf) {
    }

    private record Cached(PublicTotals totals, Instant from, Instant until) {

        boolean validAt(Instant now) {
            return !now.isBefore(from) && now.isBefore(until);
        }
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
        if (snapshot != null && snapshot.validAt(now)) {
            return snapshot.totals();
        }
        PublicTotals totals = daily.findFirstByOrderByDayDescIdDesc()
                .map(row -> new PublicTotals(row.getKgSaved(), row.getPickups(), row.getFoodsavers(), row.getStores(),
                        row.getCooperations(), row.getFoodSharePoints(), row.getBaskets(),
                        row.getUpdatedAt() == null ? now : row.getUpdatedAt()))
                .orElseGet(() -> {
                    StatisticsReadModel.Totals live = readModel.totals(now);
                    return new PublicTotals(live.kgSaved(), live.pickups(), live.foodsavers(), live.stores(),
                            live.cooperations(), live.foodSharePoints(), live.baskets(), now);
                });
        int minutes = Math.max(0, configuration.integer(ConfigurationKey.STATISTICS_PUBLIC_CACHE_MINUTES));
        cached = new Cached(totals, now, now.plus(Duration.ofMinutes(minutes)));
        return totals;
    }

    /** Drops the cache (after an aggregation run). */
    public void evict() {
        cached = null;
    }

    @Override
    public String derivedDataName() {
        return "public statistics cache";
    }

    /** The seed reset restored other {@code statistics_daily} rows: the next read takes them. */
    @Override
    public void rebuildAfterSeedReset() {
        evict();
    }
}
