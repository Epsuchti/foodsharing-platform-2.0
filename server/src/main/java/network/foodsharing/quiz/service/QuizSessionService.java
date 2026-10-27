package network.foodsharing.quiz.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.quiz.domain.AnswerRating;
import network.foodsharing.quiz.domain.Quiz;
import network.foodsharing.quiz.domain.QuizI18n;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionAction;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.repository.QuizI18nRepository;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.quiz.repository.QuizSessionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Quiz sessions (plan §13.9 Q2): start, current question, answer, results, confirmation, session history. Texts are
 * snapshotted in the user's language when the session starts (fallback de → any), so later edits never change a
 * running session. Test (practice) sessions never count as tries and have no effects; a new test session removes
 * the user's earlier test sessions of that quiz. Rules: {@link QuizRuleService}.
 */
@Service
public class QuizSessionService {

    public record SnapshotAnswer(UUID id, String text, AnswerRating rating, String explanation) {
    }

    public record SnapshotQuestion(UUID id, String text, int durationSeconds, String wikiLink, int failurePoints,
                                   List<SnapshotAnswer> answers) {
    }

    public record Given(UUID questionId, List<UUID> selected, boolean timedOut, BigDecimal failurePoints) {
    }

    public record Running(int questionCount, int questionsAnswered, boolean timed, boolean test) {
    }

    public record StatusView(QuizKind kind, QuizRuleService.TryState state, boolean mayStartNow,
                             boolean requiresConfirmation, Boolean confirmed, Running running) {
    }

    public record OverviewView(Quiz quiz, String name, String description, boolean mayTry, boolean mayRead,
                               boolean mayEdit, StatusView status) {
    }

    public record ActiveQuestion(int index, int total, SnapshotQuestion question, long ageSeconds, boolean timedOut,
                                 boolean timed) {
    }

    public record Solution(SnapshotAnswer answer, boolean selected) {
    }

    public record AnswerResult(List<Solution> solution, boolean timedOut, boolean finished, BigDecimal failurePoints) {
    }

    public record ResultQuestion(SnapshotQuestion question, Given given) {
    }

    public record ResultView(QuizSession session, List<ResultQuestion> questions, int nextWaitDays,
                             boolean requiresConfirmation, String confirmationDocument) {
    }

    public record SessionView(QuizSession session, QuizKind kind) {
    }

    /** After a non-test pass (bell QUIZ_PASSED after commit). */
    public record QuizPassed(UUID userId, QuizKind kind) {
    }

    private static final TypeReference<List<SnapshotQuestion>> QUESTIONS = new TypeReference<>() { };
    private static final TypeReference<List<Given>> GIVEN = new TypeReference<>() { };

    private final QuizRepository quizzes;
    private final QuizI18nRepository quizTexts;
    private final QuizSessionRepository sessions;
    private final QuizEditorService editor;
    private final QuizRuleService rules;
    private final UserService users;
    private final AuthSessionService authSessions;
    private final ObjectProvider<QuizConsentChecker> consents;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Random random = new SecureRandom();

    public QuizSessionService(QuizRepository quizzes, QuizI18nRepository quizTexts, QuizSessionRepository sessions,
                              QuizEditorService editor, QuizRuleService rules, UserService users,
                              AuthSessionService authSessions, ObjectProvider<QuizConsentChecker> consents,
                              CurrentActorService currentActor, PermissionService permissions, HistoryService history,
                              ApplicationEventPublisher events, ObjectMapper objectMapper, Clock clock) {
        this.quizzes = quizzes;
        this.quizTexts = quizTexts;
        this.sessions = sessions;
        this.editor = editor;
        this.rules = rules;
        this.users = users;
        this.authSessions = authSessions;
        this.consents = consents;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<OverviewView> overview() {
        SessionPrincipal actor = currentActor.requirePrincipal();
        User user = users.requireActive(actor.userId());
        String language = user.getPreferredLanguage().getValue();
        List<OverviewView> result = new ArrayList<>();
        for (QuizKind kind : QuizKind.values()) {
            Optional<Quiz> quiz = quizzes.findByKind(kind);
            if (quiz.isEmpty()) {
                continue;
            }
            Map<String, QuizI18n> texts = quizTexts.findByOwnerId(quiz.get().getId()).stream()
                    .collect(Collectors.toMap(QuizI18n::getLanguage, Function.identity()));
            QuizI18n text = pick(texts, language);
            result.add(new OverviewView(quiz.get(), text == null ? kind.name() : text.getName(),
                    text == null ? null : text.getDescription(), mayTry(actor, user, kind),
                    editor.mayRead(actor, kind), editor.mayEdit(actor, kind), status(quiz.get(), user, false)));
        }
        return result;
    }

    /** @throws ApiException 404 QUIZ_NOT_FOUND */
    @Transactional(readOnly = true)
    public StatusView status(QuizKind kind, boolean test) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        return status(editor.requireQuiz(kind), users.requireActive(actor.userId()), test);
    }

    /** Hygiene certificate validity for other modules (M3 pickups): end of validity, if passed. */
    @Transactional(readOnly = true)
    public Optional<Instant> hygieneValidUntil(UUID userId) {
        return quizzes.findByKind(QuizKind.HYGIENE).flatMap(quiz -> {
            QuizRuleService.TryState state = rules.tryState(QuizKind.HYGIENE, attempts(userId, quiz), clock.instant());
            return state.passValid() ? Optional.of(state.passedAt().plus(Duration.ofDays(365))) : Optional.empty();
        });
    }

    StatusView status(Quiz quiz, User user, boolean test) {
        QuizKind kind = quiz.getKind();
        QuizRuleService.TryState state = rules.tryState(kind, attempts(user.getId(), quiz), clock.instant());
        Optional<QuizSession> running = sessions.findFirstByUserIdAndQuizIdAndTestAndStatus(user.getId(),
                quiz.getId(), test, QuizSessionStatus.RUNNING);
        boolean requiresConfirmation = rules.requiresConfirmation(kind);
        Boolean confirmed = requiresConfirmation && state.passValid()
                ? PermissionService.isAtLeast(user.getRole(), rules.roleOf(kind)) : null;
        boolean mayStart = running.isEmpty() && (test || rules.mayStart(kind, state));
        return new StatusView(kind, state, mayStart, requiresConfirmation && state.passValid()
                && !Boolean.TRUE.equals(confirmed), confirmed,
                running.map(session -> new Running(session.getQuestionCount(), session.getQuestionIndex(),
                        session.isTimed(), session.isTest())).orElse(null));
    }

    // ------------------------------------------------------------------------------------------ session

    /**
     * @throws ApiException 404 QUIZ_NOT_FOUND, 403 QUIZ_NOT_ALLOWED, 409 QUIZ_SESSION_RUNNING / QUIZ_WAIT_TIME
     *                      (args.days) / QUIZ_DISQUALIFIED / QUIZ_ALREADY_PASSED
     */
    @Transactional
    public StatusView start(QuizKind kind, boolean timed, boolean test) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        Quiz quiz = editor.requireQuiz(kind);
        User user = users.requireActive(actor.userId());
        if (!mayTry(actor, user, kind)) {
            throw ApiException.forbidden(ApiErrorCode.QUIZ_NOT_ALLOWED);
        }
        if (sessions.findFirstByUserIdAndQuizIdAndTestAndStatus(user.getId(), quiz.getId(), test,
                QuizSessionStatus.RUNNING).isPresent()) {
            throw ApiException.conflict(ApiErrorCode.QUIZ_SESSION_RUNNING);
        }
        if (test) {
            sessions.deleteAll(sessions.findByUserIdAndQuizIdAndTest(user.getId(), quiz.getId(), true));
            sessions.flush();
        } else {
            QuizRuleService.TryState state = rules.tryState(kind, attempts(user.getId(), quiz), clock.instant());
            if (state.disqualified()) {
                throw ApiException.conflict(ApiErrorCode.QUIZ_DISQUALIFIED);
            }
            if (state.currentWaitDays() > 0) {
                throw ApiException.conflict(ApiErrorCode.QUIZ_WAIT_TIME)
                        .withArg("days", String.valueOf(state.currentWaitDays()));
            }
            if (!rules.mayStart(kind, state)) {
                throw ApiException.conflict(ApiErrorCode.QUIZ_ALREADY_PASSED);
            }
        }
        String language = user.getPreferredLanguage().getValue();
        List<SnapshotQuestion> selected = snapshot(quiz, timed ? quiz.getQuestionCountTimed()
                : quiz.getQuestionCountUntimed(), language);
        if (selected.isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.QUIZ_NOT_FOUND);
        }
        Instant now = clock.instant();
        QuizSession session = new QuizSession();
        session.setUserId(user.getId());
        session.setQuizId(quiz.getId());
        session.setStatus(QuizSessionStatus.RUNNING);
        session.setQuestions(write(selected));
        session.setResults(write(List.of()));
        session.setQuestionIndex(0);
        session.setStartedAt(now);
        session.setQuestionStartedAt(now);
        session.setMaxFailurePoints(quiz.getMaxFailurePoints());
        session.setQuestionCount(selected.size());
        session.setTimed(timed);
        session.setTest(test);
        session.setLanguage(language);
        QuizSession saved = sessions.save(session);
        history.record(history.event(QuizSessionAction.SESSION_STARTED, user.getId()).secondarySubject(quiz.getId())
                .metadata(Map.of("kind", kind.name(), "sessionId", saved.getId().toString(), "timed", timed,
                        "test", test)).build());
        return status(quiz, user, test);
    }

    /** @throws ApiException 404 QUIZ_NOT_FOUND, 409 NO_RUNNING_SESSION */
    @Transactional(readOnly = true)
    public ActiveQuestion currentQuestion(QuizKind kind, boolean test) {
        QuizSession session = running(kind, test);
        List<SnapshotQuestion> questions = read(session.getQuestions(), QUESTIONS);
        SnapshotQuestion question = questions.get(session.getQuestionIndex());
        Duration age = Duration.between(session.getQuestionStartedAt(), clock.instant());
        return new ActiveQuestion(session.getQuestionIndex(), questions.size(), question, Math.max(0, age.getSeconds()),
                rules.timedOut(session.isTimed(), age, question.durationSeconds()), session.isTimed());
    }

    /**
     * Answers the current question (null / empty = no answer); finishing the last question decides PASSED / FAILED.
     *
     * @throws ApiException 404 QUIZ_NOT_FOUND, 409 NO_RUNNING_SESSION
     */
    @Transactional
    public AnswerResult answer(QuizKind kind, boolean test, List<UUID> answerIds) {
        QuizSession session = running(kind, test);
        List<SnapshotQuestion> questions = read(session.getQuestions(), QUESTIONS);
        List<Given> given = new ArrayList<>(read(session.getResults(), GIVEN));
        SnapshotQuestion question = questions.get(session.getQuestionIndex());
        Instant now = clock.instant();
        boolean timedOut = rules.timedOut(session.isTimed(), Duration.between(session.getQuestionStartedAt(), now),
                question.durationSeconds());
        List<UUID> selected = timedOut || answerIds == null ? List.of() : answerIds.stream()
                .filter(id -> question.answers().stream().anyMatch(answer -> answer.id().equals(id))).distinct()
                .toList();
        Map<UUID, AnswerRating> ratings = question.answers().stream()
                .collect(Collectors.toMap(SnapshotAnswer::id, SnapshotAnswer::rating, (a, b) -> a, LinkedHashMap::new));
        BigDecimal points = rules.failurePoints(question.failurePoints(), ratings, selected, timedOut);
        given.add(new Given(question.id(), selected, timedOut, points));
        session.setResults(write(given));
        session.setFailurePoints(session.getFailurePoints().add(points));
        session.setQuestionIndex(session.getQuestionIndex() + 1);
        session.setQuestionStartedAt(now);
        history.record(history.event(QuizSessionAction.QUESTION_ANSWERED, session.getUserId())
                .secondarySubject(session.getQuizId())
                .metadata(Map.of("sessionId", session.getId().toString(), "questionId", question.id().toString(),
                        "failurePoints", points.toPlainString(), "timedOut", timedOut)).build());
        boolean finished = session.getQuestionIndex() >= questions.size();
        if (finished) {
            finish(session, kind);
        }
        List<Solution> solution = question.answers().stream()
                .map(answer -> new Solution(answer, selected.contains(answer.id()))).toList();
        return new AnswerResult(solution, timedOut, finished, points);
    }

    /** The latest finished session of the kind. @throws ApiException 404 QUIZ_NOT_FOUND / QUIZ_SESSION_NOT_FOUND */
    @Transactional(readOnly = true)
    public ResultView results(QuizKind kind, boolean test) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        Quiz quiz = editor.requireQuiz(kind);
        QuizSession session = sessions.findFirstByUserIdAndQuizIdAndTestAndStatusNotOrderByEndedAtDesc(actor.userId(),
                        quiz.getId(), test, QuizSessionStatus.RUNNING)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.QUIZ_SESSION_NOT_FOUND));
        List<SnapshotQuestion> questions = read(session.getQuestions(), QUESTIONS);
        Map<UUID, Given> given = read(session.getResults(), GIVEN).stream()
                .collect(Collectors.toMap(Given::questionId, Function.identity(), (a, b) -> a));
        List<ResultQuestion> result = questions.stream()
                .map(question -> new ResultQuestion(question, given.get(question.id()))).toList();
        QuizRuleService.TryState state = rules.tryState(kind, attempts(actor.userId(), quiz), clock.instant());
        User user = users.requireActive(actor.userId());
        boolean requiresConfirmation = !session.isTest() && session.getStatus() == QuizSessionStatus.PASSED
                && rules.requiresConfirmation(kind) && !PermissionService.isAtLeast(user.getRole(), rules.roleOf(kind));
        return new ResultView(session, result, session.isTest() ? 0 : state.currentWaitDays(), requiresConfirmation,
                kind == QuizKind.STORE_MANAGER ? "CONFIDENTIALITY_NOTICE"
                        : kind == QuizKind.FOODSAVER ? "LEGAL_AGREEMENT" : null);
    }

    /**
     * Confirms a passed FOODSAVER / STORE_MANAGER quiz: raises the global role (plan §9) and ends the user's other
     * sessions so they see the new role ("relogin"). The store manager confirmation needs the current
     * CONFIDENTIALITY_NOTICE consent.
     *
     * @throws ApiException 404 QUIZ_NOT_FOUND, 409 QUIZ_NOT_PASSED / CONSENT_REQUIRED (args.kind)
     */
    @Transactional
    public StatusView confirm(QuizKind kind) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        Quiz quiz = editor.requireQuiz(kind);
        User user = users.requireActive(actor.userId());
        QuizRuleService.TryState state = rules.tryState(kind, attempts(user.getId(), quiz), clock.instant());
        if (!rules.requiresConfirmation(kind) || !state.passValid()) {
            throw ApiException.conflict(ApiErrorCode.QUIZ_NOT_PASSED);
        }
        if (kind == QuizKind.STORE_MANAGER) {
            QuizConsentChecker checker = consents.getIfAvailable();
            if (checker == null || !checker.hasCurrentConsent(user.getId(), "CONFIDENTIALITY_NOTICE")) {
                throw ApiException.conflict(ApiErrorCode.CONSENT_REQUIRED).withArg("kind", "CONFIDENTIALITY_NOTICE");
            }
        }
        Role target = rules.roleOf(kind);
        history.record(history.event(QuizSessionAction.QUIZ_CONFIRMED, user.getId()).secondarySubject(quiz.getId())
                .metadata("kind", kind.name()).build());
        if (!PermissionService.isAtLeast(user.getRole(), target)) {
            Role before = user.getRole();
            users.changeRole(user.getId(), target);
            history.record(history.event(QuizSessionAction.ROLE_RAISED, user.getId()).secondarySubject(quiz.getId())
                    .change("role", before, target).build());
            authSessions.revokeOthers();
        }
        return status(quiz, user, false);
    }

    @Transactional(readOnly = true)
    public List<SessionView> mySessions(QuizKind kind) {
        return sessionsOf(currentActor.requireUserId(), kind);
    }

    /** @throws ApiException 403 unless mayAdministrateUser or editor of a quiz, 404 USER_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<SessionView> sessionsOfUser(UUID userId, QuizKind kind) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        users.require(userId);
        boolean editorOfAny = java.util.Arrays.stream(QuizKind.values()).anyMatch(k -> editor.mayEdit(actor, k));
        if (!actor.userId().equals(userId) && !editorOfAny && !permissions.mayAdministrateUser(actor, userId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return sessionsOf(userId, kind);
    }

    /** @throws ApiException 404 QUIZ_SESSION_NOT_FOUND, 403 unless editor of the session's quiz */
    @Transactional
    public void deleteSession(UUID sessionId) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        QuizSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.QUIZ_SESSION_NOT_FOUND));
        Quiz quiz = quizzes.findById(session.getQuizId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.QUIZ_SESSION_NOT_FOUND));
        if (!editor.mayEdit(actor, quiz.getKind())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        history.record(history.event(QuizSessionAction.SESSION_DELETED, session.getUserId())
                .secondarySubject(quiz.getId())
                .metadata(Map.of("sessionId", sessionId.toString(), "status", session.getStatus().name())).build());
        sessions.delete(session);
    }

    // ------------------------------------------------------------------------------------------ helpers

    private void finish(QuizSession session, QuizKind kind) {
        boolean passed = rules.passed(session.getFailurePoints(), session.getMaxFailurePoints());
        session.setStatus(passed ? QuizSessionStatus.PASSED : QuizSessionStatus.FAILED);
        session.setEndedAt(clock.instant());
        history.record(history.event(passed ? QuizSessionAction.SESSION_PASSED : QuizSessionAction.SESSION_FAILED,
                        session.getUserId()).secondarySubject(session.getQuizId())
                .metadata(Map.of("kind", kind.name(), "sessionId", session.getId().toString(),
                        "failurePoints", session.getFailurePoints().toPlainString(), "test", session.isTest()))
                .build());
        if (!passed || session.isTest()) {
            return;
        }
        Role quizRole = rules.roleOf(kind);
        if (quizRole != null) {
            User user = users.require(session.getUserId());
            if (!PermissionService.isAtLeast(user.getQuizRole(), quizRole)) {
                history.record(history.event(QuizSessionAction.QUIZ_ROLE_RAISED, user.getId())
                        .secondarySubject(session.getQuizId()).change("quizRole", user.getQuizRole(), quizRole)
                        .build());
                users.setQuizRole(user.getId(), quizRole);
            }
        }
        events.publishEvent(new QuizPassed(session.getUserId(), kind));
    }

    private boolean mayTry(SessionPrincipal actor, User user, QuizKind kind) {
        return rules.mayTry(kind, user.getRole(), user.isVerified(),
                kind == QuizKind.REPORTS && editor.isEditorGroupMember(actor, kind));
    }

    private QuizSession running(QuizKind kind, boolean test) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        Quiz quiz = editor.requireQuiz(kind);
        return sessions.findFirstByUserIdAndQuizIdAndTestAndStatus(actor.userId(), quiz.getId(), test,
                QuizSessionStatus.RUNNING).orElseThrow(() -> ApiException.conflict(ApiErrorCode.NO_RUNNING_SESSION));
    }

    private List<QuizRuleService.Attempt> attempts(UUID userId, Quiz quiz) {
        return sessions.findByUserIdAndQuizIdOrderByStartedAtAsc(userId, quiz.getId()).stream()
                .filter(session -> !session.isTest())
                .map(session -> new QuizRuleService.Attempt(session.getStatus(), session.getEndedAt())).toList();
    }

    private List<SessionView> sessionsOf(UUID userId, QuizKind kind) {
        Map<UUID, QuizKind> kinds = quizzes.findAll().stream().collect(Collectors.toMap(Quiz::getId, Quiz::getKind));
        return sessions.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(session -> new SessionView(session, kinds.get(session.getQuizId())))
                .filter(view -> kind == null || view.kind() == kind).toList();
    }

    private List<SnapshotQuestion> snapshot(Quiz quiz, int count, String language) {
        List<QuizEditorService.QuestionView> pool = editor.pool(quiz).stream()
                .filter(question -> !question.answers().isEmpty()).toList();
        Map<UUID, QuizEditorService.QuestionView> byId = pool.stream()
                .collect(Collectors.toMap(view -> view.question().getId(), Function.identity()));
        List<QuizRuleService.Candidate> chosen = rules.select(pool.stream().map(view -> new QuizRuleService.Candidate(
                view.question().getId(), view.question().getFailurePoints(), view.question().isMandatory())).toList(),
                count, random);
        List<SnapshotQuestion> result = new ArrayList<>();
        for (QuizRuleService.Candidate candidate : chosen) {
            QuizEditorService.QuestionView view = byId.get(candidate.id());
            List<SnapshotAnswer> answers = new ArrayList<>(view.answers().stream()
                    .map(answer -> new SnapshotAnswer(answer.answer().getId(), pick(answer.texts(), language),
                            answer.answer().getRating(), pick(answer.explanations(), language))).toList());
            Collections.shuffle(answers, random);
            result.add(new SnapshotQuestion(view.question().getId(), pick(view.texts(), language),
                    view.question().getDurationSeconds(), view.question().getWikiLink(),
                    view.question().getFailurePoints(), answers));
        }
        return result;
    }

    /** requested language → de → any (A14). */
    static <T> T pick(Map<String, T> byLanguage, String language) {
        if (byLanguage == null || byLanguage.isEmpty()) {
            return null;
        }
        if (byLanguage.containsKey(language)) {
            return byLanguage.get(language);
        }
        if (byLanguage.containsKey("de")) {
            return byLanguage.get("de");
        }
        return byLanguage.values().iterator().next();
    }

    private String write(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T read(String json, TypeReference<T> type) {
        return objectMapper.readValue(json == null ? "[]" : json, type);
    }
}
