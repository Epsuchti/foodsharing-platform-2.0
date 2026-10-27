package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizQuestionI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionI18nRepository extends JpaRepository<QuizQuestionI18n, UUID> {

    List<QuizQuestionI18n> findByOwnerIdIn(Collection<UUID> ownerIds);

    List<QuizQuestionI18n> findByOwnerId(UUID ownerId);
}
