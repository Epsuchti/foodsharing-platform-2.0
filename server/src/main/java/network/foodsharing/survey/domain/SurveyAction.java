package network.foodsharing.survey.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Survey history (subject SURVEY). */
public enum SurveyAction implements HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    RESPONSE_SUBMITTED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SURVEY;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
