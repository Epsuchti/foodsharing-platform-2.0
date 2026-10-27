package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    List<QuizAnswer> findByQuestionIdInAndDeletedAtIsNull(Collection<UUID> questionIds);

    Optional<QuizAnswer> findByIdAndQuestionIdAndDeletedAtIsNull(UUID id, UUID questionId);
}
