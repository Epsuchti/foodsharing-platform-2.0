package network.foodsharing.quiz.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Quiz editor history (subject = the quiz). */
public enum QuizAction implements HistoryAction {
    QUIZ_UPDATED,
    QUESTION_CREATED,
    QUESTION_UPDATED,
    QUESTION_DELETED,
    ANSWER_CREATED,
    ANSWER_UPDATED,
    ANSWER_DELETED,
    COMMENT_ADDED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.QUIZ;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
