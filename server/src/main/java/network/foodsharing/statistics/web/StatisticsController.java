package network.foodsharing.statistics.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StatisticsApi;
import network.foodsharing.api.model.AgeBand;
import network.foodsharing.api.model.GenderCount;
import network.foodsharing.api.model.MonthlyPickups;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class StatisticsController implements StatisticsApi {

    @Override
    public ResponseEntity<List<AgeBand>> getRegionAgeBands(UUID regionId, Boolean onlyHomeRegion) {
        throw ApiException.notImplemented("getRegionAgeBands");
    }

    @Override
    public ResponseEntity<List<GenderCount>> getRegionGenderStatistics(UUID regionId, Boolean onlyHomeRegion) {
        throw ApiException.notImplemented("getRegionGenderStatistics");
    }

    @Override
    public ResponseEntity<List<MonthlyPickups>> getRegionPickupStatistics(UUID regionId, Integer months) {
        throw ApiException.notImplemented("getRegionPickupStatistics");
    }
}
