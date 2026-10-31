package network.foodsharing.survey.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.survey.domain.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    List<SurveyResponse> findBySurveyIdOrderByCreatedAtAscIdAsc(UUID surveyId);

    boolean existsBySurveyIdAndUserId(UUID surveyId, UUID userId);

    long countBySurveyId(UUID surveyId);

    @Query("select r.surveyId, count(r) from SurveyResponse r where r.surveyId in :ids group by r.surveyId")
    List<Object[]> countBySurveys(@Param("ids") Collection<UUID> surveyIds);
}
