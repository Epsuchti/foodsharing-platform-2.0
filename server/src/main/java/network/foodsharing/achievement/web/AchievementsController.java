package network.foodsharing.achievement.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AchievementsApi;
import network.foodsharing.api.model.Achievement;
import network.foodsharing.api.model.AchievementCreate;
import network.foodsharing.api.model.AchievementUpdate;
import network.foodsharing.api.model.Award;
import network.foodsharing.api.model.AwardCreate;
import network.foodsharing.api.model.AwardUpdate;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class AchievementsController implements AchievementsApi {

    @Override
    public ResponseEntity<Award> awardAchievement(UUID achievementId, AwardCreate awardCreate) {
        throw ApiException.notImplemented("awardAchievement");
    }

    @Override
    public ResponseEntity<Achievement> createAchievement(AchievementCreate achievementCreate) {
        throw ApiException.notImplemented("createAchievement");
    }

    @Override
    public ResponseEntity<Void> deleteAchievement(UUID achievementId) {
        throw ApiException.notImplemented("deleteAchievement");
    }

    @Override
    public ResponseEntity<Achievement> getAchievement(UUID achievementId) {
        throw ApiException.notImplemented("getAchievement");
    }

    @Override
    public ResponseEntity<List<Achievement>> listAchievements(UUID regionId) {
        throw ApiException.notImplemented("listAchievements");
    }

    @Override
    public ResponseEntity<List<Award>> listAwards(UUID achievementId) {
        throw ApiException.notImplemented("listAwards");
    }

    @Override
    public ResponseEntity<List<Award>> listUserAchievements(UUID userId) {
        throw ApiException.notImplemented("listUserAchievements");
    }

    @Override
    public ResponseEntity<Void> revokeAward(UUID awardId) {
        throw ApiException.notImplemented("revokeAward");
    }

    @Override
    public ResponseEntity<Achievement> updateAchievement(UUID achievementId, AchievementUpdate achievementUpdate) {
        throw ApiException.notImplemented("updateAchievement");
    }

    @Override
    public ResponseEntity<Award> updateAward(UUID awardId, AwardUpdate awardUpdate) {
        throw ApiException.notImplemented("updateAward");
    }
}
