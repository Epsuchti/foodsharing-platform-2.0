package network.foodsharing.achievement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.achievement.domain.Achievement;
import network.foodsharing.achievement.domain.AchievementSystemKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    List<Achievement> findByRegionIdAndDeletedAtIsNullOrderByNameAsc(UUID regionId);

    List<Achievement> findByRegionIdIsNullAndDeletedAtIsNullOrderByNameAsc();

    Optional<Achievement> findBySystemKey(AchievementSystemKey systemKey);
}
