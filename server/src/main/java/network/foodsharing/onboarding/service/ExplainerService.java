package network.foodsharing.onboarding.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.onboarding.domain.JourneyStepKey;
import network.foodsharing.region.service.PublicRegionService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public explainer aggregate (plan §11.2): impact counters from the {@link ExplainerCounterContributor}s, the journey
 * preview (8 steps with i18n keys and typical durations) and, for a chosen region, its public card (ambassadors,
 * next EinAb dates).
 */
@Service
public class ExplainerService {

    public record StepPreview(JourneyStepKey key, String titleKey, String descriptionKey, String estimatedDuration) {
    }

    public record ExplainerView(ExplainerCounterContributor.Counters counters,
                                PublicRegionService.PublicRegionView region, List<StepPreview> journeyPreview) {
    }

    static final List<StepPreview> PREVIEW = List.of(
            preview(JourneyStepKey.ACCOUNT, "PT2M"), preview(JourneyStepKey.PROFILE_REGION, "PT5M"),
            preview(JourneyStepKey.LEARN, "PT20M"), preview(JourneyStepKey.QUIZ, "PT30M"),
            preview(JourneyStepKey.AGREEMENT, "PT5M"), preview(JourneyStepKey.INTRO_PICKUPS, "P21D"),
            preview(JourneyStepKey.ID_CHECK, "P7D"), preview(JourneyStepKey.PASSPORT, "PT2M"));

    private final List<ExplainerCounterContributor> contributors;
    private final PublicRegionService publicRegions;
    private final RegionService regions;

    public ExplainerService(List<ExplainerCounterContributor> contributors, PublicRegionService publicRegions,
                            RegionService regions) {
        this.contributors = contributors;
        this.publicRegions = publicRegions;
        this.regions = regions;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND for an unknown region */
    @Transactional(readOnly = true)
    public ExplainerView explainer(UUID regionId) {
        ExplainerCounterContributor.Counters counters = new ExplainerCounterContributor.Counters();
        contributors.forEach(contributor -> contributor.contribute(counters));
        PublicRegionService.PublicRegionView region = regionId == null ? null
                : publicRegions.region(regions.require(regionId).getSlug());
        return new ExplainerView(counters, region, PREVIEW);
    }

    private static StepPreview preview(JourneyStepKey key, String duration) {
        return new StepPreview(key, "onboarding.step." + key.name() + ".title",
                "onboarding.step." + key.name() + ".description", duration);
    }

    /** M2 counters: verified foodsavers and regions with ambassadors. */
    @Configuration(proxyBeanMethods = false)
    static class M2Counters {

        @Bean
        ExplainerCounterContributor foodsaverCounter(UserService users) {
            return counters -> counters.addFoodsavers(users.countVerifiedActive());
        }

        @Bean
        ExplainerCounterContributor regionCounter(RegionService regions) {
            return counters -> counters.addRegions(regions.countRegionsWithAmbassadors());
        }
    }
}
