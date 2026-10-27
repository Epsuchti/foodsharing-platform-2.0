package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizQuestionComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionCommentRepository extends JpaRepository<QuizQuestionComment, UUID> {

    List<QuizQuestionComment> findByQuestionIdOrderByCreatedAtAsc(UUID questionId);

    long countByQuestionId(UUID questionId);
}
