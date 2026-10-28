package network.foodsharing.achievement.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.achievement.domain.AchievementI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementI18nRepository extends JpaRepository<AchievementI18n, UUID> {

    List<AchievementI18n> findByOwnerId(UUID ownerId);

    List<AchievementI18n> findByOwnerIdIn(Collection<UUID> ownerIds);
}
