package network.foodsharing.survey.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.survey.domain.SurveyCoauthor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyCoauthorRepository extends JpaRepository<SurveyCoauthor, UUID> {

    List<SurveyCoauthor> findBySurveyIdOrderByCreatedAtAscIdAsc(UUID surveyId);

    boolean existsBySurveyIdAndUserId(UUID surveyId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SurveyCoauthor c where c.surveyId = :surveyId")
    int deleteBySurvey(@Param("surveyId") UUID surveyId);
}
