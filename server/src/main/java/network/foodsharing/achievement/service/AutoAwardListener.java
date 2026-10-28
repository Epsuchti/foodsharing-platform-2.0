package network.foodsharing.achievement.service;

import network.foodsharing.achievement.domain.AchievementSystemKey;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.store.service.StoreChainService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Automatic awards (AC1): a passed hygiene quiz awards HYGIENE_CERTIFICATE (valid
 * {@code app.achievements.hygiene-validity-days}), a new key account manager KAM_CERTIFICATE. Runs after the
 * triggering transaction committed, in its own transaction, with actor SYSTEM (history AUTO_AWARDED).
 */
@Component
public class AutoAwardListener {

    private final AchievementService achievements;
    private final AppProperties properties;

    public AutoAwardListener(AchievementService achievements, AppProperties properties) {
        this.achievements = achievements;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "AchievementService.autoAward records AUTO_AWARDED")
    public void onQuizPassed(QuizSessionService.QuizPassed event) {
        if (event.kind() == QuizKind.HYGIENE) {
            achievements.autoAward(AchievementSystemKey.HYGIENE_CERTIFICATE, event.userId(),
                    properties.getAchievements().getHygieneValidityDays());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "AchievementService.autoAward records AUTO_AWARDED")
    public void onKamAppointed(StoreChainService.KamAppointedEvent event) {
        achievements.autoAward(AchievementSystemKey.KAM_CERTIFICATE, event.userId(), null);
    }
}
