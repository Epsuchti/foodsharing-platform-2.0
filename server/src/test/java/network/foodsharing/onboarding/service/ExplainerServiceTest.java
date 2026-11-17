package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.onboarding.domain.JourneyStepKey;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.PublicRegionService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.statistics.service.PublicStatisticsService;
import org.junit.jupiter.api.Test;

/**
 * Plan §11.2 public explainer: the counters are the sum of every contributor, the journey preview has the 8 steps
 * (EinAbs sixth, no duration for the steps that depend on others), and a region is shown only when asked for.
 */
class ExplainerServiceTest {

    private final PublicRegionService publicRegions = mock(PublicRegionService.class);
    private final RegionService regions = mock(RegionService.class);
    private final ExplainerService service = new ExplainerService(List.of(
            counters -> counters.addFoodsavers(9), counters -> counters.addFoodsavers(3),
            counters -> counters.addRegions(1)), publicRegions, regions);

    @Test
    void countersAddUpAndThePreviewHasEightSteps() {
        ExplainerService.ExplainerView view = service.explainer(null);
        assertThat(view.counters().foodsavers()).isEqualTo(12);
        assertThat(view.counters().regions()).isEqualTo(1);
        assertThat(view.region()).isNull();
        assertThat(view.journeyPreview()).hasSize(8);
        assertThat(view.journeyPreview().get(5).key()).isEqualTo(JourneyStepKey.INTRO_PICKUPS);
        assertThat(view.journeyPreview().get(5).estimatedDuration()).isNull();
        assertThat(view.journeyPreview().get(0).estimatedDuration()).isEqualTo("PT2M");
        assertThat(view.journeyPreview().get(0).titleKey()).isEqualTo("onboarding.step.ACCOUNT.title");
    }

    /** The landing showed "0 kg / 0 Abholungen": nothing contributed the M3 counters. */
    @Test
    void kilogramsPickupsAndStoresAreThePublicPlatformTotals() {
        PublicStatisticsService statistics = mock(PublicStatisticsService.class);
        when(statistics.current()).thenReturn(new PublicStatisticsService.PublicTotals(1481927.0, 217925, 10997, 1549,
                1086, 169, 60379, java.time.Instant.parse("2026-11-01T01:30:00Z")));
        ExplainerService withStatistics = new ExplainerService(List.of(
                new ExplainerService.StatisticsCounters().statisticsCounter(statistics),
                counters -> counters.addFoodsavers(9)), publicRegions, regions);
        ExplainerCounterContributor.Counters counters = withStatistics.explainer(null).counters();
        assertThat(counters.kgSaved()).isEqualTo(1481927.0);
        assertThat(counters.pickups()).isEqualTo(217925);
        assertThat(counters.stores()).isEqualTo(1549);
        // the foodsaver counter stays the verified active accounts (M2), not the statistics row
        assertThat(counters.foodsavers()).isEqualTo(9);
    }

    @Test
    void aChosenRegionIsShownAndAnUnknownOneIs404() {
        UUID zurich = UUID.randomUUID();
        Region region = new Region();
        region.setSlug("zuerich-stadt");
        when(regions.require(zurich)).thenReturn(region);
        service.explainer(zurich);
        verify(publicRegions).region("zuerich-stadt");

        UUID unknown = UUID.randomUUID();
        when(regions.require(unknown)).thenThrow(ApiException.notFound(ApiErrorCode.REGION_NOT_FOUND));
        assertThatThrownBy(() -> service.explainer(unknown)).isInstanceOfSatisfying(ApiException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.REGION_NOT_FOUND));
    }
}
