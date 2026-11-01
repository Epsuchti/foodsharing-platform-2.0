package network.foodsharing.automation.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import network.foodsharing.api.model.MetricKey;
import network.foodsharing.api.model.MetricSeries;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;

/** {@link AutomationMapper#toSeries} (AU9): unknown keys are left out, known series stay. */
class AutomationMapperMetricsTest {

    private final AutomationMapper mapper = new AutomationMapper(mock(UserMapper.class), mock(UserService.class));

    @Test
    void unknownKeyIsLeftOutAndKnownSeriesStay() {
        LocalDate day = LocalDate.of(2026, 10, 1);
        Map<String, Map<LocalDate, Long>> series = new TreeMap<>();
        series.put("ADVERT.SENT", Map.of(day, 3L));
        series.put("OLD.ARCHIVED_KEY", Map.of(day, 7L));
        series.put("SLOT_DECLINE_RULE.GAP", Map.of(day, 2L));

        List<MetricSeries> result = mapper.toSeries(series);

        assertThat(result).extracting(MetricSeries::getKey)
                .containsExactly(MetricKey.ADVERT_SENT, MetricKey.SLOT_DECLINE_RULE_GAP);
        assertThat(result.get(1).getPoints()).singleElement()
                .satisfies(p -> assertThat(p.getValue()).isEqualTo(2L));
    }
}
