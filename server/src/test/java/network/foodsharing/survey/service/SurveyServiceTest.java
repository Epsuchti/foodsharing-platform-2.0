package network.foodsharing.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.survey.domain.SurveyQuestion;
import network.foodsharing.survey.domain.SurveyQuestionType;
import network.foodsharing.survey.service.SurveyService.AnswerInput;
import network.foodsharing.survey.service.SurveyService.QuestionInput;
import org.junit.jupiter.api.Test;

/** Definition and answer validation of the reference survey port (SV1). */
class SurveyServiceTest {

    static final String STORE = UUID.randomUUID().toString();
    final SurveyQuestion store = question(SurveyQuestionType.STORE, true);
    final SurveyQuestion single = question(SurveyQuestionType.SINGLE_CHOICE, true);
    final SurveyQuestion multi = question(SurveyQuestionType.MULTIPLE_CHOICE, false);
    final SurveyQuestion bool = question(SurveyQuestionType.BOOLEAN, false);
    final SurveyQuestion text = question(SurveyQuestionType.TEXT, false);
    final List<SurveyQuestion> all = List.of(store, single, multi, bool, text);

    @Test
    void definitionNeedsTitleQuestionsOptionsAndAtMostOneStoreQuestion() {
        QuestionInput choice = new QuestionInput(null, SurveyQuestionType.SINGLE_CHOICE, "Q", true, List.of("a", "b"));
        QuestionInput storeQ = new QuestionInput(null, SurveyQuestionType.STORE, "Store", false, List.of());
        SurveyService.validateDefinition("T", List.of(choice, storeQ));
        assertInvalid(() -> SurveyService.validateDefinition(" ", List.of(choice)), ApiErrorCode.VALIDATION_FAILED);
        assertInvalid(() -> SurveyService.validateDefinition("T", List.of()), ApiErrorCode.VALIDATION_FAILED);
        assertInvalid(() -> SurveyService.validateDefinition("T", List.of(storeQ, storeQ)),
                ApiErrorCode.VALIDATION_FAILED);
        assertInvalid(() -> SurveyService.validateDefinition("T", List.of(new QuestionInput(null,
                SurveyQuestionType.MULTIPLE_CHOICE, "Q", false, List.of("a", " a ", ""))))
                , ApiErrorCode.VALIDATION_FAILED);
    }

    @Test
    void answersAreCheckedPerTypeAndTheStoreComesFromTheLink() {
        Map<UUID, List<String>> cleaned = clean(List.of(
                new AnswerInput(single.getId(), List.of("b")),
                new AnswerInput(multi.getId(), List.of("a", "b")),
                new AnswerInput(bool.getId(), List.of("true")),
                new AnswerInput(text.getId(), List.of(" Hallo "))));
        assertThat(cleaned.get(store.getId())).containsExactly(STORE);
        assertThat(cleaned.get(text.getId())).containsExactly("Hallo");
        assertThat(clean(List.of(new AnswerInput(single.getId(), List.of("a"))))).hasSize(2);

        assertInvalid(() -> clean(List.of()), ApiErrorCode.SURVEY_QUESTION_REQUIRED);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("x")))),
                ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a", "b")))),
                ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a")),
                new AnswerInput(multi.getId(), List.of("a", "a")))), ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a")),
                new AnswerInput(bool.getId(), List.of("yes")))), ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a")),
                new AnswerInput(text.getId(), List.of("x".repeat(4001))))), ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a")),
                new AnswerInput(UUID.randomUUID(), List.of("a")))), ApiErrorCode.SURVEY_ANSWER_INVALID);
        assertInvalid(() -> clean(List.of(new AnswerInput(single.getId(), List.of("a")),
                new AnswerInput(store.getId(), List.of(UUID.randomUUID().toString())))),
                ApiErrorCode.STORE_ANSWER_LINK_ONLY);
    }

    private Map<UUID, List<String>> clean(List<AnswerInput> answers) {
        return SurveyService.cleanAnswers(all, answers, STORE, json -> List.of("a", "b", "c"));
    }

    private static void assertInvalid(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(code));
    }

    private static SurveyQuestion question(SurveyQuestionType type, boolean required) {
        SurveyQuestion question = new SurveyQuestion();
        question.setId(UUID.randomUUID());
        question.setType(type);
        question.setRequired(required);
        return question;
    }
}
