package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID quizId);

    Optional<QuizQuestion> findByIdAndQuizIdAndDeletedAtIsNull(UUID id, UUID quizId);
}
