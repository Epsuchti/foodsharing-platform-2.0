package network.foodsharing.survey.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.survey.domain.Survey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyRepository extends JpaRepository<Survey, UUID> {

    /** Surveys the user owns or co-authors, newest first. */
    @Query("""
            select s from Survey s where s.ownerId = :userId
              or exists (select c.id from SurveyCoauthor c where c.surveyId = s.id and c.userId = :userId)
            order by s.createdAt desc, s.id""")
    List<Survey> listAccessible(@Param("userId") UUID userId);
}
