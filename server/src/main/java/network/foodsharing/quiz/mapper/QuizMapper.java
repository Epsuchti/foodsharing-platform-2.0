package network.foodsharing.quiz.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import network.foodsharing.api.model.ActiveQuestion;
import network.foodsharing.api.model.AnswerDetail;
import network.foodsharing.api.model.AnswerRating;
import network.foodsharing.api.model.AnswerResult;
import network.foodsharing.api.model.AnswerSolution;
import network.foodsharing.api.model.LegalDocumentKind;
import network.foodsharing.api.model.NextTryInfo;
import network.foodsharing.api.model.QuestionComment;
import network.foodsharing.api.model.QuestionDetail;
import network.foodsharing.api.model.QuizAnswerOption;
import network.foodsharing.api.model.QuizDetail;
import network.foodsharing.api.model.QuizKind;
import network.foodsharing.api.model.QuizOverview;
import network.foodsharing.api.model.QuizQuestionView;
import network.foodsharing.api.model.QuizResult;
import network.foodsharing.api.model.QuizResultAnswer;
import network.foodsharing.api.model.QuizResultQuestion;
import network.foodsharing.api.model.QuizRunningInfo;
import network.foodsharing.api.model.QuizSessionStatus;
import network.foodsharing.api.model.QuizSessionView;
import network.foodsharing.api.model.QuizStatus;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.quiz.service.QuizEditorService;
import network.foodsharing.quiz.service.QuizSessionService;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Quiz service views ↔ generated models. */
@Component
public class QuizMapper {

    private final UserMapper users;

    public QuizMapper(UserMapper users) {
        this.users = users;
    }

    public QuizStatus toStatus(QuizSessionService.StatusView view) {
        QuizStatus status = new QuizStatus();
        var state = view.state();
        status.setLastSessionStatus(state.lastStatus() == null ? null
                : QuizSessionStatus.fromValue(state.lastStatus().name()));
        status.setTriesSinceLastPass(state.triesSinceLastPass());
        status.setCurrentWaitDays(state.currentWaitDays());
        status.setWaitDaysAfterNextFailure(state.waitDaysAfterNextFailure());
        status.setExpiresInDays(state.expiresInDays());
        status.setConfirmed(view.confirmed());
        if (view.running() != null) {
            QuizRunningInfo running = new QuizRunningInfo();
            running.setQuestionCount(view.running().questionCount());
            running.setQuestionsAnswered(view.running().questionsAnswered());
            running.setTimed(view.running().timed());
            running.setTest(view.running().test());
            status.setRunning(running);
        }
        status.setMayStartNow(view.mayStartNow());
        status.setRequiresConfirmation(view.requiresConfirmation());
        status.setPassedAt(TimeMapper.toOffset(state.passedAt()));
        return status;
    }

    public QuizOverview toOverview(QuizSessionService.OverviewView view) {
        QuizOverview overview = new QuizOverview();
        overview.setKind(kind(view.quiz().getKind()));
        overview.setName(view.name());
        overview.setDescription(view.description());
        overview.setQuestionCountTimed(view.quiz().getQuestionCountTimed());
        overview.setQuestionCountUntimed(view.quiz().getQuestionCountUntimed());
        overview.setMaxFailurePoints(view.quiz().getMaxFailurePoints());
        overview.setMayTry(view.mayTry());
        overview.setMayRead(view.mayRead());
        overview.setMayEdit(view.mayEdit());
        overview.setStatus(toStatus(view.status()));
        return overview;
    }

    public ActiveQuestion toActive(QuizSessionService.ActiveQuestion view) {
        ActiveQuestion active = new ActiveQuestion();
        active.setIndex(view.index());
        active.setTotal(view.total());
        QuizQuestionView question = new QuizQuestionView();
        question.setId(view.question().id());
        question.setText(view.question().text());
        question.setDurationSeconds(view.timed() ? view.question().durationSeconds() : null);
        question.setWikiLink(view.question().wikiLink());
        question.setAnswers(view.question().answers().stream().map(answer -> {
            QuizAnswerOption option = new QuizAnswerOption();
            option.setId(answer.id());
            option.setText(answer.text());
            return option;
        }).toList());
        active.setQuestion(question);
        active.setQuestionAgeSeconds((int) view.ageSeconds());
        active.setTimedOut(view.timedOut());
        return active;
    }

    public AnswerResult toAnswerResult(QuizSessionService.AnswerResult result) {
        AnswerResult model = new AnswerResult();
        model.setSolution(result.solution().stream().map(solution -> {
            AnswerSolution answer = new AnswerSolution();
            answer.setId(solution.answer().id());
            answer.setText(solution.answer().text());
            answer.setRating(AnswerRating.fromValue(solution.answer().rating().name()));
            answer.setSelected(solution.selected());
            answer.setExplanation(solution.answer().explanation());
            return answer;
        }).toList());
        model.setTimedOut(result.timedOut());
        model.setFinished(result.finished());
        model.setFailurePoints(result.failurePoints().doubleValue());
        return model;
    }

    public QuizResult toResult(QuizSessionService.ResultView view) {
        QuizResult result = new QuizResult();
        result.setStatus(QuizSessionStatus.fromValue(view.session().getStatus().name()));
        result.setFailurePoints(view.session().getFailurePoints().doubleValue());
        result.setMaxFailurePoints(view.session().getMaxFailurePoints());
        result.setQuestions(view.questions().stream().map(question -> {
            QuizResultQuestion model = new QuizResultQuestion();
            model.setText(question.question().text());
            model.setWikiLink(question.question().wikiLink());
            boolean answered = question.given() != null;
            model.setTimedOut(answered && question.given().timedOut());
            model.setUserFailurePoints(answered ? question.given().failurePoints().doubleValue() : 0.0);
            model.setAnswers(question.question().answers().stream().map(answer -> {
                QuizResultAnswer resultAnswer = new QuizResultAnswer();
                resultAnswer.setText(answer.text());
                resultAnswer.setRating(AnswerRating.fromValue(answer.rating().name()));
                resultAnswer.setSelected(answered && question.given().selected().contains(answer.id()));
                resultAnswer.setExplanation(answer.explanation());
                return resultAnswer;
            }).toList());
            return model;
        }).toList());
        NextTryInfo next = new NextTryInfo();
        next.setWaitDays(Math.max(view.nextWaitDays(), 0));
        next.setDisqualified(view.nextWaitDays() < 0);
        result.setNextTryInfo(next);
        result.setRequiresConfirmation(view.requiresConfirmation());
        result.setConfirmationDocument(view.requiresConfirmation() && view.confirmationDocument() != null
                ? LegalDocumentKind.fromValue(view.confirmationDocument()) : null);
        result.setTest(view.session().isTest());
        return result;
    }

    public QuizSessionView toSession(QuizSessionService.SessionView view) {
        var session = view.session();
        QuizSessionView model = new QuizSessionView();
        model.setId(session.getId());
        model.setUserId(session.getUserId());
        model.setKind(kind(view.kind()));
        model.setStatus(QuizSessionStatus.fromValue(session.getStatus().name()));
        model.setStartedAt(TimeMapper.toOffset(session.getStartedAt()));
        model.setEndedAt(TimeMapper.toOffset(session.getEndedAt()));
        model.setFailurePoints(session.getFailurePoints().doubleValue());
        model.setMaxFailurePoints(session.getMaxFailurePoints());
        model.setQuestionCount(session.getQuestionCount());
        model.setTimed(session.isTimed());
        model.setTest(session.isTest());
        return model;
    }

    public QuizDetail toDetail(QuizEditorService.QuizDetailView view) {
        QuizDetail detail = new QuizDetail();
        detail.setKind(kind(view.quiz().getKind()));
        detail.setNames(new LinkedHashMap<>(view.names()));
        detail.setDescriptions(new LinkedHashMap<>(view.descriptions()));
        detail.setMaxFailurePoints(view.quiz().getMaxFailurePoints());
        detail.setQuestionCountTimed(view.quiz().getQuestionCountTimed());
        detail.setQuestionCountUntimed(view.quiz().getQuestionCountUntimed());
        detail.setQuestionCount(view.questionCount());
        detail.setMayEdit(view.mayEdit());
        return detail;
    }

    public QuestionDetail toQuestion(QuizEditorService.QuestionView view) {
        QuestionDetail detail = new QuestionDetail();
        detail.setId(view.question().getId());
        detail.setTexts(new LinkedHashMap<>(view.texts()));
        detail.setDurationSeconds(view.question().getDurationSeconds());
        detail.setWikiLink(view.question().getWikiLink());
        detail.setMandatory(view.question().isMandatory());
        detail.setFailurePoints(view.question().getFailurePoints());
        detail.setAnswers(view.answers().stream().map(this::toAnswer).toList());
        detail.setCommentCount((int) view.commentCount());
        return detail;
    }

    public AnswerDetail toAnswer(QuizEditorService.AnswerView view) {
        AnswerDetail detail = new AnswerDetail();
        detail.setId(view.answer().getId());
        detail.setTexts(new LinkedHashMap<>(view.texts()));
        detail.setExplanations(new LinkedHashMap<>(view.explanations()));
        detail.setRating(AnswerRating.fromValue(view.answer().getRating().name()));
        return detail;
    }

    public QuestionComment toComment(QuizEditorService.CommentView view) {
        QuestionComment comment = new QuestionComment();
        comment.setId(view.comment().getId());
        comment.setAuthor(view.author() == null ? null : users.toSummary(view.author()));
        comment.setText(view.comment().getText());
        comment.setCreatedAt(TimeMapper.toOffset(view.comment().getCreatedAt()));
        return comment;
    }

    public static QuizKind kind(network.foodsharing.quiz.domain.QuizKind kind) {
        return kind == null ? null : QuizKind.fromValue(kind.name());
    }

    public static network.foodsharing.quiz.domain.QuizKind kind(QuizKind kind) {
        return kind == null ? null : network.foodsharing.quiz.domain.QuizKind.valueOf(kind.name());
    }

    public static network.foodsharing.quiz.domain.AnswerRating rating(AnswerRating rating) {
        return rating == null ? null : network.foodsharing.quiz.domain.AnswerRating.valueOf(rating.name());
    }
}
