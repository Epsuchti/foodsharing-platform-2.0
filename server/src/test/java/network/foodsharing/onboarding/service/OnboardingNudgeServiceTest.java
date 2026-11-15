package network.foodsharing.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.onboarding.domain.JourneyStepStatus;
import network.foodsharing.onboarding.domain.OnboardingNudge;
import network.foodsharing.onboarding.repository.OnboardingNudgeRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.onboarding.service.JourneyCalculator.StepView;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * EA8 / EA21 (plan §11.4): the daily stalled-newcomer nudge. A newcomer without progress for 30 days gets one bell and
 * mail that lead back to the journey, at most once per 30 days; newcomers waiting for others (EinAbs booked, ID check)
 * and newcomers with recent progress get none.
 */
class OnboardingNudgeServiceTest {

    static final Instant SEED = Instant.parse("2026-11-02T09:00:00Z");

    private final JourneyService journeys = mock(JourneyService.class);
    private final UserService users = mock(UserService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OnboardingNudgeRepository nudges = mock(OnboardingNudgeRepository.class);
    private final List<OnboardingNudge> sent = new ArrayList<>();
    private Instant now = SEED;

    private User newcomer;
    private User waiting;

    @BeforeEach
    void setUp() {
        newcomer = user("newbie@test.local");
        waiting = user("pending@test.local");
        when(users.findActiveUnverified()).thenReturn(List.of(newcomer, waiting));
        // both registered at the seed date and did nothing since
        when(journeys.lastProgressAt(any())).thenReturn(SEED.minus(Duration.ofDays(1)));
        when(journeys.compute(newcomer.getId())).thenReturn(journey("PROFILE_REGION", JourneyStepStatus.AVAILABLE));
        when(journeys.compute(waiting.getId())).thenReturn(journey("ID_CHECK", JourneyStepStatus.WAITING_FOR_OTHERS));
        when(nudges.save(any())).thenAnswer(call -> {
            sent.add(call.getArgument(0));
            return call.getArgument(0);
        });
        when(nudges.existsByUserIdAndKindAndSentAtAfter(any(), anyString(), any())).thenAnswer(call -> sent.stream()
                .anyMatch(n -> n.getUserId().equals(call.getArgument(0)) && n.getKind().equals(call.getArgument(1))
                        && n.getSentAt().isAfter(call.getArgument(2))));
    }

    private static User user(String email) {
        User user = TestUsers.user(email, Role.FOODSHARER);
        user.setId(UUID.randomUUID());
        return user;
    }

    private static JourneyView journey(String current, JourneyStepStatus status) {
        StepView step = new StepView(current, status, null, null, null, null, null, null, null, null, false, null);
        return new JourneyView(false, current, List.of(step), null, List.of(), List.of());
    }

    private int runAt(int daysAfterSeed) {
        now = SEED.plus(Duration.ofDays(daysAfterSeed));
        return new OnboardingNudgeService(journeys, users, notifications, nudges, mock(HistoryService.class, RETURNS_DEEP_STUBS),
                Clock.fixed(now, ZoneOffset.UTC)).sendStalledNudges();
    }

    @Test
    void nothingIsStalledWithin30Days() {
        assertThat(runAt(0)).isZero();
        assertThat(runAt(28)).isZero();
        verify(notifications, never()).notify(any());
    }

    @Test
    void aStalledNewcomerIsNudgedOnceWithBellAndMailBackToTheJourney() {
        assertThat(runAt(40)).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.ONBOARDING_STALLED);
        assertThat(request.getValue().recipientIds()).containsExactly(newcomer.getId());
        assertThat(request.getValue().link()).isEqualTo("/onboarding");
        assertThat(request.getValue().mailTemplate()).isEqualTo("onboarding/stalled");
        assertThat(request.getValue().bellOnly()).isFalse();
        assertThat(request.getValue().params()).containsEntry("step", "PROFILE_REGION");

        // the next day's run does not repeat it, 30 days later it does again
        assertThat(runAt(41)).isZero();
        assertThat(runAt(69)).isZero();
        assertThat(runAt(71)).isEqualTo(1);
        verify(notifications, times(2)).notify(any());
    }

    @Test
    void newcomersWaitingForOthersOrWithRecentProgressAreNotNudged() {
        when(journeys.lastProgressAt(newcomer)).thenReturn(SEED.plus(Duration.ofDays(35)));
        assertThat(runAt(40)).isZero();
        verify(notifications, never()).notify(any());
        verify(journeys).compute(eq(waiting.getId()));
    }

    @Test
    void aCompleteJourneyIsNeverNudged() {
        when(journeys.compute(newcomer.getId())).thenReturn(new JourneyView(true, null, List.of(), null, List.of(), List.of()));
        assertThat(runAt(40)).isZero();
    }
}
