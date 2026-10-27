package network.foodsharing.onboarding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.service.IntroPickupGuideService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.onboarding.domain.JourneyStepKey;
import network.foodsharing.onboarding.domain.JourneyStepStatus;
import network.foodsharing.onboarding.domain.OnboardingAction;
import network.foodsharing.onboarding.domain.OnboardingNudge;
import network.foodsharing.onboarding.repository.OnboardingNudgeRepository;
import network.foodsharing.onboarding.service.JourneyCalculator.JourneyView;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserVerifiedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Onboarding bells (plan §11.4): ONBOARDING_STALLED (bell + mail {@code onboarding/stalled}) for newcomers without
 * progress for 30 days, at most once per 30 days; ONBOARDING_STEP_UNBLOCKED when a waiting step was resolved by
 * others (verified, the last EinAb marked done, foodsaver quiz passed, region step confirmed). Bell params:
 * {@code step} = the new current step key.
 */
@Service
public class OnboardingNudgeService {

    static final Duration STALLED_AFTER = Duration.ofDays(30);
    static final String STALLED = "STALLED";

    private final JourneyService journeys;
    private final UserService users;
    private final NotificationService notifications;
    private final OnboardingNudgeRepository nudges;
    private final HistoryService history;
    private final Clock clock;

    public OnboardingNudgeService(JourneyService journeys, UserService users, NotificationService notifications,
                                  OnboardingNudgeRepository nudges, HistoryService history, Clock clock) {
        this.journeys = journeys;
        this.users = users;
        this.notifications = notifications;
        this.nudges = nudges;
        this.history = history;
        this.clock = clock;
    }

    /** @return the number of nudged newcomers */
    @Transactional
    public int sendStalledNudges() {
        Instant now = clock.instant();
        int sent = 0;
        for (User user : users.findActiveUnverified()) {
            if (nudges.existsByUserIdAndKindAndSentAtAfter(user.getId(), STALLED, now.minus(STALLED_AFTER))) {
                continue;
            }
            JourneyView journey = journeys.compute(user.getId());
            if (journey.complete() || !journeys.lastProgressAt(user).isBefore(now.minus(STALLED_AFTER))
                    || waitsForOthers(journey)) {
                continue;
            }
            notifications.notify(NotificationRequest.to(List.of(user.getId()), NotificationType.ONBOARDING_STALLED)
                    .param("step", journey.currentStep()).link("/onboarding").mail("onboarding/stalled")
                    .identifier(BellService.identifier(NotificationType.ONBOARDING_STALLED, user.getId()))
                    .icon("compass").build());
            OnboardingNudge nudge = new OnboardingNudge();
            nudge.setUserId(user.getId());
            nudge.setKind(STALLED);
            nudge.setSentAt(now);
            nudges.save(nudge);
            history.record(history.event(OnboardingAction.NUDGE_SENT, user.getId()).metadata("kind", STALLED)
                    .metadata("step", journey.currentStep()).build());
            sent++;
        }
        return sent;
    }

    /** Newcomers waiting for others (booked EinAbs, verification) are not stalled by their own fault. */
    private static boolean waitsForOthers(JourneyView journey) {
        return journey.steps().stream().anyMatch(s -> s.key().equals(journey.currentStep())
                && s.status() == JourneyStepStatus.WAITING_FOR_OTHERS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "bells record NOTIFICATION SENT through the notification module")
    public void onVerified(UserVerifiedEvent event) {
        unblocked(event.userId(), JourneyStepKey.ID_CHECK);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "bells record NOTIFICATION SENT through the notification module")
    public void onIntroPickupDone(IntroPickupGuideService.IntroPickupMarkedDone event) {
        unblocked(event.userId(), JourneyStepKey.INTRO_PICKUPS);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "bells record NOTIFICATION SENT through the notification module")
    public void onQuizPassed(QuizSessionService.QuizPassed event) {
        if (event.kind() == QuizKind.FOODSAVER) {
            unblocked(event.userId(), JourneyStepKey.QUIZ);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "bells record NOTIFICATION SENT through the notification module")
    public void onRegionStepConfirmed(JourneyService.RegionStepConfirmed event) {
        unblocked(event.userId(), null);
    }

    /** Bell when the resolved step is DONE now (or, for region steps, always) and the journey goes on. */
    void unblocked(java.util.UUID userId, JourneyStepKey resolved) {
        if (users.findActive(userId).isEmpty()) {
            return;
        }
        JourneyView journey = journeys.compute(userId);
        boolean resolvedDone = resolved == null || journey.steps().stream()
                .anyMatch(s -> s.key().equals(resolved.name()) && s.status() == JourneyStepStatus.DONE);
        if (!resolvedDone || journey.complete()) {
            return;
        }
        notifications.notify(NotificationRequest.to(List.of(userId), NotificationType.ONBOARDING_STEP_UNBLOCKED)
                .param("step", journey.currentStep()).link("/onboarding").icon("unlock").build());
    }
}
