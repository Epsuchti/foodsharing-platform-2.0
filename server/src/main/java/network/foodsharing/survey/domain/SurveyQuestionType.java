package network.foodsharing.survey.domain;

/** Survey question type; STORE questions are only answered through the store link. */
public enum SurveyQuestionType {
    TEXT,
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    BOOLEAN,
    STORE
}
