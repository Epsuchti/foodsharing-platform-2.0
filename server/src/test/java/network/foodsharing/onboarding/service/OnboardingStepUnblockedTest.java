package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.onboarding.repository.OnboardingNudgeRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Plan §11.4: a confirmed region step rings the newcomer (ONBOARDING_STEP_UNBLOCKED) unless the journey is over. */
class OnboardingStepUnblockedTest {

    private final JourneyService journeys = mock(JourneyService.class);
    private final UserService users = mock(UserService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OnboardingNudgeService service = new OnboardingNudgeService(journeys, users, notifications,
            mock(OnboardingNudgeRepository.class), mock(HistoryService.class, RETURNS_DEEP_STUBS),
            Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC));
    private final UUID newcomer = UUID.randomUUID();

    private void journey(boolean complete, String current) {
        when(users.findActive(newcomer)).thenReturn(Optional.of(new User()));
        when(journeys.compute(newcomer)).thenReturn(new JourneyView(complete, current, List.of(), null, List.of(),
                List.of()));
    }

    @Test
    void aConfirmedRegionStepRingsTheNewcomerWithTheNextStep() {
        journey(false, "ID_CHECK");
        service.onRegionStepConfirmed(new JourneyService.RegionStepConfirmed(newcomer, UUID.randomUUID(),
                "newcomer-meeting"));
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.ONBOARDING_STEP_UNBLOCKED);
        assertThat(request.getValue().recipientIds()).containsExactly(newcomer);
        assertThat(request.getValue().params()).containsEntry("step", "ID_CHECK");
        assertThat(request.getValue().link()).isEqualTo("/onboarding");
    }

    @Test
    void aCompleteJourneyOrAnInactiveUserGetsNoBell() {
        journey(true, null);
        service.onRegionStepConfirmed(new JourneyService.RegionStepConfirmed(newcomer, UUID.randomUUID(), "x"));
        when(users.findActive(newcomer)).thenReturn(Optional.empty());
        service.onRegionStepConfirmed(new JourneyService.RegionStepConfirmed(newcomer, UUID.randomUUID(), "x"));
        verify(notifications, never()).notify(any());
    }
}
