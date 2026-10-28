package network.foodsharing.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.survey.domain.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, UUID> {

    List<SurveyAnswer> findByResponseIdIn(Collection<UUID> responseIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from SurveyAnswer a where a.questionId = :questionId")
    int deleteByQuestion(@Param("questionId") UUID questionId);
}
