package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.Quiz;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.quiz.repository.QuizSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/** Q2: the hygiene-expiry reminder rings at most once per 30 days per person and names the days left. */
class HygieneReminderIntervalTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void oneReminderPer30DaysWithTheDaysLeft() {
        NotificationService notifications = mock(NotificationService.class);
        QuizRepository quizzes = mock(QuizRepository.class);
        QuizSessionRepository sessions = mock(QuizSessionRepository.class);
        Quiz quiz = new Quiz();
        quiz.setId(UUID.randomUUID());
        when(quizzes.findByKind(QuizKind.HYGIENE)).thenReturn(Optional.of(quiz));
        QuizSession pass = new QuizSession();
        pass.setUserId(UUID.randomUUID());
        pass.setStatus(QuizSessionStatus.PASSED);
        pass.setEndedAt(NOW.minus(Duration.ofDays(300)));
        when(sessions.findByQuizIdAndStatusAndTestFalse(quiz.getId(), QuizSessionStatus.PASSED))
                .thenReturn(List.of(pass));
        QuizNotifications service = new QuizNotifications(notifications, quizzes, sessions, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(ObjectProvider.class));
        String identifier = "HYGIENE_EXPIRING-" + pass.getUserId();

        when(notifications.sentSince(eq(identifier), eq(NOW.minus(Duration.ofDays(30))))).thenReturn(false);
        assertThat(service.remindHygieneExpiring()).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(request.capture());
        assertThat(request.getValue().type()).isEqualTo(NotificationType.HYGIENE_EXPIRING);
        assertThat(request.getValue().identifier()).isEqualTo(identifier);
        assertThat(request.getValue().params()).containsEntry("days", "65");

        NotificationService reminded = mock(NotificationService.class);
        when(reminded.sentSince(any(), any())).thenReturn(true);
        QuizNotifications again = new QuizNotifications(reminded, quizzes, sessions, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(ObjectProvider.class));
        assertThat(again.remindHygieneExpiring()).isZero();
        verify(reminded, never()).notify(any());
    }
}
