package network.foodsharing.quiz.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of quiz sessions (A40): sessions are removed, question comments stay (anonymised author).
 */
@Component
public class QuizAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public QuizAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "QuizSession", "userId", userId);
    }

    @Override
    public String section() {
        return "quiz";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessions", UserRows.read(em, "QuizSession", "userId", userId, "quizId", "status", "startedAt", "endedAt", "results"));
        data.put("comments", UserRows.read(em, "QuizQuestionComment", "authorId", userId, "questionId", "text", "createdAt"));
        return data;
    }
}
