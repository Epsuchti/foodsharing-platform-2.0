package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.quiz.domain.Quiz;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.quiz.repository.QuizSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** B9: the HYGIENE_CERTIFICATE award's end wins over the quiz pass for the expiry reminder. */
class HygieneCertificateExpiryTest {

    static final Instant NOW = Instant.parse("2026-10-28T08:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void awardEndDecidesTheReminder() {
        NotificationService notifications = mock(NotificationService.class);
        QuizRepository quizzes = mock(QuizRepository.class);
        QuizSessionRepository sessions = mock(QuizSessionRepository.class);
        Quiz quiz = new Quiz();
        quiz.setId(UUID.randomUUID());
        when(quizzes.findByKind(QuizKind.HYGIENE)).thenReturn(Optional.of(quiz));
        QuizSession pass = new QuizSession();
        pass.setUserId(UUID.randomUUID());
        pass.setStatus(QuizSessionStatus.PASSED);
        // passed 360 days ago: inside the warning window by the quiz rule
        pass.setEndedAt(NOW.minusSeconds(360L * 86400));
        when(sessions.findByQuizIdAndStatusAndTestFalse(quiz.getId(), QuizSessionStatus.PASSED))
                .thenReturn(List.of(pass));
        ObjectProvider<HygieneCertificates> provider = mock(ObjectProvider.class);
        QuizNotifications service = new QuizNotifications(notifications, quizzes, sessions, new AppProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC), provider);
        assertThat(service.remindHygieneExpiring()).isEqualTo(1);
        // the renewed certificate award runs another year: no reminder
        when(provider.getIfAvailable()).thenReturn(user -> Optional.of(LocalDate.of(2027, 10, 1)));
        assertThat(service.remindHygieneExpiring()).isZero();
        when(provider.getIfAvailable()).thenReturn(user -> Optional.empty());
        when(notifications.sentSince(any(), any())).thenReturn(false);
        assertThat(service.remindHygieneExpiring()).isEqualTo(1);
    }
}
