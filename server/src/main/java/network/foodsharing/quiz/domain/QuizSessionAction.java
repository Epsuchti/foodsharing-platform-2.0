package network.foodsharing.quiz.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Quiz session history (subject = the user, secondary subject = the quiz). */
public enum QuizSessionAction implements HistoryAction {
    SESSION_STARTED,
    QUESTION_ANSWERED,
    SESSION_PASSED,
    SESSION_FAILED,
    SESSION_DELETED,
    QUIZ_CONFIRMED,
    QUIZ_ROLE_RAISED,
    ROLE_RAISED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.USER;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.ACCOUNT_LIFETIME;
    }
}
