package network.foodsharing.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.survey.domain.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, UUID> {

    List<SurveyQuestion> findBySurveyIdOrderBySortOrder(UUID surveyId);

    @Query("select q.surveyId, count(q) from SurveyQuestion q where q.surveyId in :ids group by q.surveyId")
    List<Object[]> countBySurveys(@Param("ids") Collection<UUID> surveyIds);
}
