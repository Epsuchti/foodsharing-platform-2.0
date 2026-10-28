package network.foodsharing.survey.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of surveys (A40): responses stay for the results (anonymised respondent), co-authorships
 * are removed, owned surveys stay (anonymised owner).
 */
@Component
public class SurveyAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public SurveyAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "SurveyCoauthor", "userId", userId);
    }

    @Override
    public String section() {
        return "surveys";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("owned", UserRows.read(em, "Survey", "ownerId", userId, "title", "createdAt"));
        data.put("coauthored", UserRows.read(em, "SurveyCoauthor", "userId", userId, "surveyId"));
        data.put("answers", em.createQuery("select r.surveyId, a.questionId, a.valuesJson, r.createdAt from SurveyResponse r, "
                + "SurveyAnswer a where a.responseId = r.id and r.userId = :userId").setParameter("userId", userId)
                .getResultList());
        return data;
    }
}
