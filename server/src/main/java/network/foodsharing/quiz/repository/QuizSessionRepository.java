package network.foodsharing.quiz.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    List<QuizSession> findByUserIdAndQuizIdOrderByStartedAtAscIdAsc(UUID userId, UUID quizId);

    List<QuizSession> findByUserIdInAndQuizIdOrderByStartedAtAscIdAsc(java.util.Collection<UUID> userIds, UUID quizId);

    List<QuizSession> findByUserIdOrderByStartedAtDescIdDesc(UUID userId);

    Optional<QuizSession> findFirstByUserIdAndQuizIdAndTestAndStatus(UUID userId, UUID quizId, boolean test,
                                                                       QuizSessionStatus status);

    Optional<QuizSession> findFirstByUserIdAndQuizIdAndTestAndStatusNotOrderByEndedAtDescIdDesc(
            UUID userId, UUID quizId, boolean test, QuizSessionStatus status);

    List<QuizSession> findByUserIdAndQuizIdAndTest(UUID userId, UUID quizId, boolean test);

    List<QuizSession> findByQuizIdAndStatusAndTestFalse(UUID quizId, QuizSessionStatus status);
}
