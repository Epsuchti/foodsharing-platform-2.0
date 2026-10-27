package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizAnswerI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerI18nRepository extends JpaRepository<QuizAnswerI18n, UUID> {

    List<QuizAnswerI18n> findByOwnerIdIn(Collection<UUID> ownerIds);

    List<QuizAnswerI18n> findByOwnerId(UUID ownerId);
}
