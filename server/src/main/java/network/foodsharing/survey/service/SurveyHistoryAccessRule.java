package network.foodsharing.survey.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** SURVEY history: owner and co-authors (ORGA through the history module). */
@Component
public class SurveyHistoryAccessRule implements HistoryAccessRule {

    private final SurveyService surveys;

    public SurveyHistoryAccessRule(SurveyService surveys) {
        this.surveys = surveys;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SURVEY;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return surveys.isOwnerOrCoauthor(subjectId, actor.userId());
    }
}
