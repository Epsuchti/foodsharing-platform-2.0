package network.foodsharing.quiz.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.quiz.repository.QuizSessionRepository;
import network.foodsharing.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Quiz bells: QUIZ_PASSED after a pass (after commit) and HYGIENE_EXPIRING inside the warning window, at most once per
 * 30 days per user (identifier {@code HYGIENE_EXPIRING-<userId>}).
 */
@Service
public class QuizNotifications {

    static final Duration REMINDER_INTERVAL = Duration.ofDays(30);

    private final NotificationService notifications;
    private final QuizRepository quizzes;
    private final QuizSessionRepository sessions;
    private final AppProperties properties;
    private final Clock clock;
    private final ObjectProvider<HygieneCertificates> certificates;

    public QuizNotifications(NotificationService notifications, QuizRepository quizzes,
                             QuizSessionRepository sessions, AppProperties properties, Clock clock,
                             ObjectProvider<HygieneCertificates> certificates) {
        this.certificates = certificates;
        this.notifications = notifications;
        this.quizzes = quizzes;
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onQuizPassed(QuizSessionService.QuizPassed event) {
        notifications.notify(NotificationRequest.to(List.of(event.userId()), NotificationType.QUIZ_PASSED)
                .param("kind", event.kind().name()).link("/quizzes/" + event.kind().name() + "/results")
                .icon("graduation-cap").build());
    }

    /** @return the number of reminders sent */
    @Transactional
    public int remindHygieneExpiring() {
        var quiz = quizzes.findByKind(QuizKind.HYGIENE);
        if (quiz.isEmpty()) {
            return 0;
        }
        var now = clock.instant();
        var validity = Duration.ofDays(properties.getQuiz().getHygieneValidityDays());
        var warning = Duration.ofDays(properties.getQuiz().getHygieneWarningDays());
        Map<UUID, QuizSession> latestPass = new java.util.HashMap<>();
        for (QuizSession session : sessions.findByQuizIdAndStatusAndTestFalse(quiz.get().getId(),
                QuizSessionStatus.PASSED)) {
            latestPass.merge(session.getUserId(), session,
                    (a, b) -> a.getEndedAt().isAfter(b.getEndedAt()) ? a : b);
        }
        int sent = 0;
        for (QuizSession pass : latestPass.values()) {
            var expiry = certificateExpiry(pass.getUserId()).orElse(pass.getEndedAt().plus(validity));
            if (expiry.isBefore(now) || expiry.minus(warning).isAfter(now)) {
                continue;
            }
            String identifier = BellService.identifier(NotificationType.HYGIENE_EXPIRING, pass.getUserId());
            if (notifications.sentSince(identifier, now.minus(REMINDER_INTERVAL))) {
                continue;
            }
            notifications.notify(NotificationRequest.to(List.of(pass.getUserId()), NotificationType.HYGIENE_EXPIRING)
                    .param("days", String.valueOf(Duration.between(now, expiry).toDays()))
                    .link("/quizzes/HYGIENE").identifier(identifier).icon("shield").build());
            sent++;
        }
        return sent;
    }

    /** B9: the HYGIENE_CERTIFICATE award's end (end of its last valid day in Zurich) wins over the quiz pass. */
    private java.util.Optional<Instant> certificateExpiry(UUID userId) {
        HygieneCertificates source = certificates.getIfAvailable();
        if (source == null) {
            return java.util.Optional.empty();
        }
        return source.validUntil(userId)
                .map(day -> day.plusDays(1).atStartOfDay(ZoneId.of("Europe/Zurich")).toInstant());
    }
}
