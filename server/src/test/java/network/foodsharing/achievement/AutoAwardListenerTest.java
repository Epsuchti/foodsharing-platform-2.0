package network.foodsharing.achievement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import network.foodsharing.achievement.domain.AchievementSystemKey;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.achievement.service.AutoAwardListener;
import network.foodsharing.config.AppProperties;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.store.service.StoreChainService;
import org.junit.jupiter.api.Test;

/** AC1: hygiene quiz → HYGIENE_CERTIFICATE (configured validity), KAM appointment → KAM_CERTIFICATE. */
class AutoAwardListenerTest {

    AchievementService achievements = mock(AchievementService.class);
    AppProperties properties = new AppProperties();
    AutoAwardListener listener = new AutoAwardListener(achievements, properties);

    @Test
    void hygienePassAwardsTheCertificateOtherQuizzesNot() {
        properties.getAchievements().setHygieneValidityDays(200);
        UUID user = UUID.randomUUID();
        listener.onQuizPassed(new QuizSessionService.QuizPassed(user, QuizKind.FOODSAVER));
        verify(achievements, never()).autoAward(any(), any(), any());
        listener.onQuizPassed(new QuizSessionService.QuizPassed(user, QuizKind.HYGIENE));
        verify(achievements).autoAward(AchievementSystemKey.HYGIENE_CERTIFICATE, user, 200);
    }

    @Test
    void kamAppointmentAwardsTheKamCertificate() {
        UUID user = UUID.randomUUID();
        listener.onKamAppointed(new StoreChainService.KamAppointedEvent(user, UUID.randomUUID()));
        verify(achievements).autoAward(AchievementSystemKey.KAM_CERTIFICATE, user, null);
    }
}
