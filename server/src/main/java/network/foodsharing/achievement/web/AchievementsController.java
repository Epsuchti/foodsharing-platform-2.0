package network.foodsharing.achievement.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.achievement.mapper.AchievementMapper;
import network.foodsharing.achievement.service.AchievementService;
import network.foodsharing.api.AchievementsApi;
import network.foodsharing.api.model.Achievement;
import network.foodsharing.api.model.AchievementCreate;
import network.foodsharing.api.model.AchievementUpdate;
import network.foodsharing.api.model.Award;
import network.foodsharing.api.model.AwardCreate;
import network.foodsharing.api.model.AwardUpdate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Achievements (AC1): definitions, awards and a member's visible awards. */
@RestController
public class AchievementsController implements AchievementsApi {

    private final AchievementService service;
    private final AchievementMapper mapper;

    public AchievementsController(AchievementService service, AchievementMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<Achievement>> listAchievements(UUID regionId) {
        return ResponseEntity.ok(mapper.toAchievements(service.list(regionId)));
    }

    @Override
    public ResponseEntity<Achievement> createAchievement(AchievementCreate achievementCreate) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toAchievement(service.create(mapper.toInput(achievementCreate))));
    }

    @Override
    public ResponseEntity<Achievement> getAchievement(UUID achievementId) {
        return ResponseEntity.ok(mapper.toAchievement(service.get(achievementId)));
    }

    @Override
    public ResponseEntity<Achievement> updateAchievement(UUID achievementId, AchievementUpdate achievementUpdate) {
        return ResponseEntity.ok(mapper.toAchievement(service.update(achievementId,
                mapper.toInput(achievementUpdate))));
    }

    @Override
    public ResponseEntity<Void> deleteAchievement(UUID achievementId) {
        service.delete(achievementId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Award>> listAwards(UUID achievementId) {
        return ResponseEntity.ok(mapper.toAwards(service.awards(achievementId)));
    }

    @Override
    public ResponseEntity<Award> awardAchievement(UUID achievementId, AwardCreate awardCreate) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAward(service.award(achievementId,
                awardCreate.getUserId(), awardCreate.getValidUntil(), awardCreate.getNotice())));
    }

    @Override
    public ResponseEntity<Award> updateAward(UUID awardId, AwardUpdate awardUpdate) {
        return ResponseEntity.ok(mapper.toAward(service.updateAward(awardId, awardUpdate.getValidUntil(),
                Boolean.TRUE.equals(awardUpdate.getClearValidUntil()),
                awardUpdate.getNotice())));
    }

    @Override
    public ResponseEntity<Void> revokeAward(UUID awardId) {
        service.revoke(awardId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<Award>> listUserAchievements(UUID userId) {
        return ResponseEntity.ok(mapper.toAwards(service.userAwards(userId)));
    }
}
