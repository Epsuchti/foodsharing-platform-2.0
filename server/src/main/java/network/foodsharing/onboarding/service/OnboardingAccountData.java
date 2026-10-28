package network.foodsharing.onboarding.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of the onboarding journey (A40): progress, confirmed steps and nudges are removed.
 */
@Component
public class OnboardingAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public OnboardingAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "LearningProgress", "userId", userId);
        UserRows.delete(em, "UserOnboardingStepDone", "userId", userId);
        UserRows.delete(em, "OnboardingNudge", "userId", userId);
    }

    @Override
    public String section() {
        return "onboarding";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("readChapters", UserRows.read(em, "LearningProgress", "userId", userId, "chapterKey", "readAt"));
        data.put("stepsDone", UserRows.read(em, "UserOnboardingStepDone", "userId", userId, "stepId", "confirmedAt"));
        data.put("nudges", UserRows.read(em, "OnboardingNudge", "userId", userId, "kind", "sentAt"));
        return data;
    }
}
