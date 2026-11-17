package network.foodsharing.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.statistics.domain.StatisticsAction;
import network.foodsharing.statistics.domain.StatisticsDaily;
import network.foodsharing.statistics.repository.StatisticsDailyRepository;
import network.foodsharing.statistics.repository.StatisticsReadModel;
import network.foodsharing.statistics.service.StatisticsAggregationService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** C3: one statistics row per Zurich day (a second run updates it), history entries, user counters (was StatisticsFlowIT). */
class StatisticsAggregationServiceTest {

    /** 23:30 UTC is already the next day in Zurich. */
    static final Instant NOW = Instant.parse("2026-11-02T23:30:00Z");

    StatisticsDailyRepository daily = mock(StatisticsDailyRepository.class);
    StatisticsReadModel readModel = mock(StatisticsReadModel.class);
    UserService users = mock(UserService.class);
    HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    StatisticsAggregationService service = new StatisticsAggregationService(daily, readModel, users, history,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(readModel.totals(NOW)).thenReturn(new StatisticsReadModel.Totals(12.5, 7, 5, 25, 20, 6, 8));
        when(readModel.userFigures(NOW)).thenReturn(Map.of(UUID.randomUUID(),
                new StatisticsReadModel.UserFigures(3, 4.5, 1, 0)));
        when(daily.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void writesTodaysTotalsOnTheZurichDayAndRefreshesTheUserCounters() {
        when(daily.findByDay(any())).thenReturn(Optional.empty());
        StatisticsDaily row = service.aggregate();
        assertThat(row.getDay()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(row.getPickups()).isEqualTo(7);
        assertThat(row.getStores()).isEqualTo(25);
        assertThat(row.getKgSaved()).isEqualTo(12.5);
        verify(history).event(eq(StatisticsAction.AGGREGATED), any());
        verify(history).event(eq(StatisticsAction.USER_STATS_REFRESHED), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<UUID, UserService.StatisticFigures>> figures = ArgumentCaptor.forClass(Map.class);
        verify(users).refreshStatistics(figures.capture(), eq(NOW));
        assertThat(figures.getValue().values()).containsExactly(new UserService.StatisticFigures(3, 4.5, 1, 0));
    }

    @Test
    void aSecondRunOnTheSameDayUpdatesTheRowInsteadOfAddingOne() {
        StatisticsDaily existing = new StatisticsDaily();
        existing.setDay(LocalDate.of(2026, 11, 3));
        existing.setPickups(1);
        when(daily.findByDay(LocalDate.of(2026, 11, 3))).thenReturn(Optional.of(existing));
        assertThat(service.aggregate()).isSameAs(existing);
        assertThat(existing.getPickups()).isEqualTo(7);
        verify(daily).save(existing);
    }
}
