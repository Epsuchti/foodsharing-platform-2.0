package network.foodsharing.achievement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.achievement.domain.AwardedAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AwardedAchievementRepository extends JpaRepository<AwardedAchievement, UUID> {

    List<AwardedAchievement> findByAchievementIdOrderByAwardedAtDescIdDesc(UUID achievementId);

    List<AwardedAchievement> findByUserIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(UUID userId);

    List<AwardedAchievement> findByUserIdAndAchievementIdAndRevokedAtIsNullOrderByAwardedAtDescIdDesc(UUID userId,
                                                                                               UUID achievementId);

    @Query("select a.achievementId, count(a) from AwardedAchievement a where a.revokedAt is null"
            + " and a.achievementId in :ids group by a.achievementId")
    List<Object[]> countActive(@Param("ids") Collection<UUID> ids);

    @Modifying
    @Query("delete from AwardedAchievement a where a.userId = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
