package network.foodsharing.onboarding.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.onboarding.domain.LearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningProgressRepository extends JpaRepository<LearningProgress, UUID> {

    List<LearningProgress> findByUserId(UUID userId);

    boolean existsByUserIdAndChapterKey(UUID userId, String chapterKey);
}
