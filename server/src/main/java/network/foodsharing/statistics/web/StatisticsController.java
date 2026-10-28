package network.foodsharing.statistics.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StatisticsApi;
import network.foodsharing.api.model.AgeBand;
import network.foodsharing.api.model.GenderCount;
import network.foodsharing.api.model.MonthlyPickups;
import network.foodsharing.statistics.service.RegionStatisticsQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Region statistics tab (R3/R13). */
@RestController
public class StatisticsController implements StatisticsApi {

    private final RegionStatisticsQueryService statistics;

    public StatisticsController(RegionStatisticsQueryService statistics) {
        this.statistics = statistics;
    }

    @Override
    public ResponseEntity<List<AgeBand>> getRegionAgeBands(UUID regionId, Boolean onlyHomeRegion) {
        return ResponseEntity.ok(statistics.ageBands(regionId, Boolean.TRUE.equals(onlyHomeRegion)).stream()
                .map(c -> new AgeBand().label(c.label()).count(c.count())).toList());
    }

    @Override
    public ResponseEntity<List<GenderCount>> getRegionGenderStatistics(UUID regionId, Boolean onlyHomeRegion) {
        return ResponseEntity.ok(statistics.genders(regionId, Boolean.TRUE.equals(onlyHomeRegion)).stream()
                .map(c -> new GenderCount().gender(c.label()).count(c.count())).toList());
    }

    @Override
    public ResponseEntity<List<MonthlyPickups>> getRegionPickupStatistics(UUID regionId, Integer months) {
        return ResponseEntity.ok(statistics.monthlyPickups(regionId, months == null ? 12 : months).stream()
                .map(m -> new MonthlyPickups().month(m.month()).pickups(m.pickups()).kg(m.kg())).toList());
    }
}
