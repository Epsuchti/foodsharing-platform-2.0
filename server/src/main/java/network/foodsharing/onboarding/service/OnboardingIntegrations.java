package network.foodsharing.onboarding.service;

import network.foodsharing.security.SessionInfoBuilder;
import network.foodsharing.security.SessionInfoEnricher;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** SessionInfo.onboarding: whether the journey is complete and its current step (header journey bar). */
@Configuration(proxyBeanMethods = false)
public class OnboardingIntegrations {

    @Bean
    @Order(30)
    SessionInfoEnricher onboardingSessionInfoEnricher(JourneyService journeys) {
        return (SessionPrincipal principal, SessionInfoBuilder builder) -> {
            JourneyCalculator.JourneyView journey = journeys.compute(principal.userId());
            builder.onboarding(journey.complete(), journey.currentStep());
        };
    }
}
