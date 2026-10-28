package network.foodsharing.statistics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.statistics.domain.StatisticsAction;
import network.foodsharing.statistics.domain.StatisticsDaily;
import network.foodsharing.statistics.repository.StatisticsDailyRepository;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import network.foodsharing.statistics.repository.StatisticsReadModel.Totals;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly platform totals (C3): one {@code statistics_daily} row per Zurich day (idempotent: a second run on the same
 * day overwrites the row) and the {@code stat_*} counters of every active account.
 */
@Service
public class StatisticsAggregationService {

    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private final StatisticsDailyRepository daily;
    private final StatisticsReadModel readModel;
    private final UserService users;
    private final HistoryService history;
    private final Clock clock;

    public StatisticsAggregationService(StatisticsDailyRepository daily, StatisticsReadModel readModel,
                                        UserService users, HistoryService history, Clock clock) {
        this.daily = daily;
        this.readModel = readModel;
        this.users = users;
        this.history = history;
        this.clock = clock;
    }

    /** Writes today's totals and refreshes the user counters. */
    @Transactional
    public StatisticsDaily aggregate() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        Totals totals = readModel.totals(now);
        StatisticsDaily row = daily.findByDay(today).orElseGet(() -> {
            StatisticsDaily created = new StatisticsDaily();
            created.setDay(today);
            return created;
        });
        row.setKgSaved(totals.kgSaved());
        row.setPickups(totals.pickups());
        row.setFoodsavers(totals.foodsavers());
        row.setStores(totals.stores());
        row.setCooperations(totals.cooperations());
        row.setFoodSharePoints(totals.foodSharePoints());
        row.setBaskets(totals.baskets());
        StatisticsDaily saved = daily.save(row);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("day", today.toString());
        metadata.put("pickups", totals.pickups());
        metadata.put("kgSaved", totals.kgSaved());
        history.record(history.event(StatisticsAction.AGGREGATED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(metadata).build());

        Map<UUID, UserService.StatisticFigures> figures = new HashMap<>();
        readModel.userFigures(now).forEach((id, f) -> figures.put(id,
                new UserService.StatisticFigures(f.pickups(), f.kg(), f.posts(), f.baskets())));
        int refreshed = users.refreshStatistics(figures, now);
        history.record(history.event(StatisticsAction.USER_STATS_REFRESHED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("users", refreshed)).build());
        return saved;
    }
}
