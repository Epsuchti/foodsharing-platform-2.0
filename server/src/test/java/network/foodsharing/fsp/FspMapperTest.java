package network.foodsharing.fsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.FspSummary;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.mapper.FspMapper;
import network.foodsharing.fsp.service.FspQueryService.FspSummaryView;
import org.junit.jupiter.api.Test;

/** List entries carry the position, so a region's FSP list can pin every entry (incl. inactive ones) on its map. */
class FspMapperTest {

    private final FspMapper mapper = new FspMapper(null, null);

    @Test
    void summaryCarriesThePosition() {
        FoodSharePoint inactive = fsp("Fairteiler Langstrasse", FspStatus.INACTIVE);
        inactive.setLat(47.3782);
        inactive.setLon(8.5262);
        FoodSharePoint unplaced = fsp("Ohne Ort", FspStatus.ACTIVE);

        List<FspSummary> summaries = mapper.toSummaries(List.of(new FspSummaryView(inactive, 3, true),
                new FspSummaryView(unplaced, 0, false)));

        assertThat(summaries.get(0).getLat()).isEqualTo(47.3782);
        assertThat(summaries.get(0).getLon()).isEqualTo(8.5262);
        assertThat(summaries.get(0).getStatus()).isEqualTo(network.foodsharing.api.model.FspStatus.INACTIVE);
        assertThat(summaries.get(1).getLat()).isNull();
        assertThat(summaries.get(1).getLon()).isNull();
    }

    private static FoodSharePoint fsp(String name, FspStatus status) {
        FoodSharePoint fsp = new FoodSharePoint();
        fsp.setId(UUID.randomUUID());
        fsp.setName(name);
        fsp.setCity("Zürich");
        fsp.setStatus(status);
        return fsp;
    }
}
