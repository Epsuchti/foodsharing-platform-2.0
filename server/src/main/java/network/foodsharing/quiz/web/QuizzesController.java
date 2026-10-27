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
import network.foodsharing.quiz.mapper.QuizMapper;
import network.foodsharing.quiz.service.QuizEditorService;
import network.foodsharing.quiz.service.QuizSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Quiz API (plan §13.9 Q1–Q3). */
@RestController
public class QuizzesController implements QuizzesApi {

    private final QuizSessionService sessions;
    private final QuizEditorService editor;
    private final QuizMapper mapper;

    public QuizzesController(QuizSessionService sessions, QuizEditorService editor, QuizMapper mapper) {
        this.sessions = sessions;
        this.editor = editor;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<QuestionComment> addQuestionComment(QuizKind kind, UUID questionId,
                                                              QuestionCommentCreate body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toComment(editor.addComment(QuizMapper.kind(kind), questionId, body.getText())));
    }

    @Override
    public ResponseEntity<AnswerResult> answerQuestion(QuizKind kind, SelectedAnswers body, Boolean test) {
        return ResponseEntity.ok(mapper.toAnswerResult(sessions.answer(QuizMapper.kind(kind), Boolean.TRUE.equals(test),
                body == null ? null : body.getAnswerIds())));
    }

    @Override
    public ResponseEntity<QuizStatus> confirmQuiz(QuizKind kind) {
        return ResponseEntity.ok(mapper.toStatus(sessions.confirm(QuizMapper.kind(kind))));
    }

    @Override
    public ResponseEntity<AnswerDetail> createAnswer(QuizKind kind, UUID questionId, AnswerUpdate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAnswer(editor.createAnswer(QuizMapper.kind(kind),
                questionId, new QuizEditorService.AnswerInput(body.getTexts(), body.getExplanations(),
                        QuizMapper.rating(body.getRating())))));
    }

    @Override
    public ResponseEntity<QuestionDetail> createQuestion(QuizKind kind, QuestionUpdate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toQuestion(editor.createQuestion(
                QuizMapper.kind(kind), question(body))));
    }

    @Override
    public ResponseEntity<Void> deleteAnswer(QuizKind kind, UUID questionId, UUID answerId) {
        editor.deleteAnswer(QuizMapper.kind(kind), questionId, answerId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteQuestion(QuizKind kind, UUID questionId) {
        editor.deleteQuestion(QuizMapper.kind(kind), questionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteQuizSession(UUID sessionId) {
        sessions.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ActiveQuestion> getNextQuestion(QuizKind kind, Boolean test) {
        return ResponseEntity.ok(mapper.toActive(sessions.currentQuestion(QuizMapper.kind(kind),
                Boolean.TRUE.equals(test))));
    }

    @Override
    public ResponseEntity<QuizDetail> getQuiz(QuizKind kind) {
        return ResponseEntity.ok(mapper.toDetail(editor.quiz(QuizMapper.kind(kind))));
    }

    @Override
    public ResponseEntity<QuizResult> getQuizResults(QuizKind kind, Boolean test) {
        return ResponseEntity.ok(mapper.toResult(sessions.results(QuizMapper.kind(kind), Boolean.TRUE.equals(test))));
    }

    @Override
    public ResponseEntity<QuizStatus> getQuizStatus(QuizKind kind, Boolean test) {
        return ResponseEntity.ok(mapper.toStatus(sessions.status(QuizMapper.kind(kind), Boolean.TRUE.equals(test))));
    }

    @Override
    public ResponseEntity<List<QuizSessionView>> listMyQuizSessions(QuizKind kind) {
        return ResponseEntity.ok(sessions.mySessions(QuizMapper.kind(kind)).stream().map(mapper::toSession).toList());
    }

    @Override
    public ResponseEntity<List<QuestionComment>> listQuestionComments(QuizKind kind, UUID questionId) {
        return ResponseEntity.ok(editor.comments(QuizMapper.kind(kind), questionId).stream()
                .map(mapper::toComment).toList());
    }

    @Override
    public ResponseEntity<List<QuestionDetail>> listQuestions(QuizKind kind) {
        return ResponseEntity.ok(editor.questions(QuizMapper.kind(kind)).stream().map(mapper::toQuestion).toList());
    }

    @Override
    public ResponseEntity<List<QuizOverview>> listQuizzes() {
        return ResponseEntity.ok(sessions.overview().stream().map(mapper::toOverview).toList());
    }

    @Override
    public ResponseEntity<List<QuizSessionView>> listUserQuizSessions(UUID userId, QuizKind kind) {
        return ResponseEntity.ok(sessions.sessionsOfUser(userId, QuizMapper.kind(kind)).stream()
                .map(mapper::toSession).toList());
    }

    @Override
    public ResponseEntity<QuizStatus> startQuizSession(QuizKind kind, StartQuizSession body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toStatus(sessions.start(QuizMapper.kind(kind),
                Boolean.TRUE.equals(body.getTimed()), Boolean.TRUE.equals(body.getTest()))));
    }

    @Override
    public ResponseEntity<AnswerDetail> updateAnswer(QuizKind kind, UUID questionId, UUID answerId, AnswerUpdate body) {
        return ResponseEntity.ok(mapper.toAnswer(editor.updateAnswer(QuizMapper.kind(kind), questionId, answerId,
                new QuizEditorService.AnswerInput(body.getTexts(), body.getExplanations(),
                        QuizMapper.rating(body.getRating())))));
    }

    @Override
    public ResponseEntity<QuestionDetail> updateQuestion(QuizKind kind, UUID questionId, QuestionUpdate body) {
        return ResponseEntity.ok(mapper.toQuestion(editor.updateQuestion(QuizMapper.kind(kind), questionId,
                question(body))));
    }

    @Override
    public ResponseEntity<QuizDetail> updateQuiz(QuizKind kind, QuizUpdate body) {
        return ResponseEntity.ok(mapper.toDetail(editor.updateQuiz(QuizMapper.kind(kind), new QuizEditorService.QuizInput(
                body.getNames(), body.getDescriptions(), body.getMaxFailurePoints(), body.getQuestionCountTimed(),
                body.getQuestionCountUntimed(), body.getReason()))));
    }

    private static QuizEditorService.QuestionInput question(QuestionUpdate body) {
        return new QuizEditorService.QuestionInput(body.getTexts(), body.getDurationSeconds(), body.getWikiLink(),
                body.getMandatory(), body.getFailurePoints());
    }
}
