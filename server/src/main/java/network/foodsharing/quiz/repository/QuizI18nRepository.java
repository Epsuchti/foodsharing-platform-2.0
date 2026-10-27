package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizI18nRepository extends JpaRepository<QuizI18n, UUID> {

    List<QuizI18n> findByOwnerIdIn(Collection<UUID> ownerIds);

    List<QuizI18n> findByOwnerId(UUID ownerId);
}
