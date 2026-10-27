package network.foodsharing.quiz.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.QuizzesApi;
import network.foodsharing.api.model.ActiveQuestion;
import network.foodsharing.api.model.AnswerDetail;
import network.foodsharing.api.model.AnswerResult;
import network.foodsharing.api.model.AnswerUpdate;
import network.foodsharing.api.model.QuestionComment;
import network.foodsharing.api.model.QuestionCommentCreate;
import network.foodsharing.api.model.QuestionDetail;
import network.foodsharing.api.model.QuestionUpdate;
import network.foodsharing.api.model.QuizDetail;
import network.foodsharing.api.model.QuizKind;
import network.foodsharing.api.model.QuizOverview;
import network.foodsharing.api.model.QuizResult;
import network.foodsharing.api.model.QuizSessionView;
import network.foodsharing.api.model.QuizStatus;
import network.foodsharing.api.model.QuizUpdate;
import network.foodsharing.api.model.SelectedAnswers;
import network.foodsharing.api.model.StartQuizSession;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quiz API (implemented in T13).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m2.md).
 */
@RestController
public class QuizzesController implements QuizzesApi {

    @Override
    public ResponseEntity<QuestionComment> addQuestionComment(QuizKind kind, UUID questionId, QuestionCommentCreate questionCommentCreate) {
        throw ApiException.notImplemented("addQuestionComment");
    }

    @Override
    public ResponseEntity<AnswerResult> answerQuestion(QuizKind kind, SelectedAnswers selectedAnswers, Boolean test) {
        throw ApiException.notImplemented("answerQuestion");
    }

    @Override
    public ResponseEntity<QuizStatus> confirmQuiz(QuizKind kind) {
        throw ApiException.notImplemented("confirmQuiz");
    }

    @Override
    public ResponseEntity<AnswerDetail> createAnswer(QuizKind kind, UUID questionId, AnswerUpdate answerUpdate) {
        throw ApiException.notImplemented("createAnswer");
    }

    @Override
    public ResponseEntity<QuestionDetail> createQuestion(QuizKind kind, QuestionUpdate questionUpdate) {
        throw ApiException.notImplemented("createQuestion");
    }

    @Override
    public ResponseEntity<Void> deleteAnswer(QuizKind kind, UUID questionId, UUID answerId) {
        throw ApiException.notImplemented("deleteAnswer");
    }

    @Override
    public ResponseEntity<Void> deleteQuestion(QuizKind kind, UUID questionId) {
        throw ApiException.notImplemented("deleteQuestion");
    }

    @Override
    public ResponseEntity<Void> deleteQuizSession(UUID sessionId) {
        throw ApiException.notImplemented("deleteQuizSession");
    }

    @Override
    public ResponseEntity<ActiveQuestion> getNextQuestion(QuizKind kind, Boolean test) {
        throw ApiException.notImplemented("getNextQuestion");
    }

    @Override
    public ResponseEntity<QuizDetail> getQuiz(QuizKind kind) {
        throw ApiException.notImplemented("getQuiz");
    }

    @Override
    public ResponseEntity<QuizResult> getQuizResults(QuizKind kind, Boolean test) {
        throw ApiException.notImplemented("getQuizResults");
    }

    @Override
    public ResponseEntity<QuizStatus> getQuizStatus(QuizKind kind, Boolean test) {
        throw ApiException.notImplemented("getQuizStatus");
    }

    @Override
    public ResponseEntity<List<QuizSessionView>> listMyQuizSessions(QuizKind kind) {
        throw ApiException.notImplemented("listMyQuizSessions");
    }

    @Override
    public ResponseEntity<List<QuestionComment>> listQuestionComments(QuizKind kind, UUID questionId) {
        throw ApiException.notImplemented("listQuestionComments");
    }

    @Override
    public ResponseEntity<List<QuestionDetail>> listQuestions(QuizKind kind) {
        throw ApiException.notImplemented("listQuestions");
    }

    @Override
    public ResponseEntity<List<QuizOverview>> listQuizzes() {
        throw ApiException.notImplemented("listQuizzes");
    }

    @Override
    public ResponseEntity<List<QuizSessionView>> listUserQuizSessions(UUID userId, QuizKind kind) {
        throw ApiException.notImplemented("listUserQuizSessions");
    }

    @Override
    public ResponseEntity<QuizStatus> startQuizSession(QuizKind kind, StartQuizSession startQuizSession) {
        throw ApiException.notImplemented("startQuizSession");
    }

    @Override
    public ResponseEntity<AnswerDetail> updateAnswer(QuizKind kind, UUID questionId, UUID answerId, AnswerUpdate answerUpdate) {
        throw ApiException.notImplemented("updateAnswer");
    }

    @Override
    public ResponseEntity<QuestionDetail> updateQuestion(QuizKind kind, UUID questionId, QuestionUpdate questionUpdate) {
        throw ApiException.notImplemented("updateQuestion");
    }

    @Override
    public ResponseEntity<QuizDetail> updateQuiz(QuizKind kind, QuizUpdate quizUpdate) {
        throw ApiException.notImplemented("updateQuiz");
    }
}
