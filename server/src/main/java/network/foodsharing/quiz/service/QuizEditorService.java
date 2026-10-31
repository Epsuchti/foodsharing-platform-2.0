package network.foodsharing.quiz.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.quiz.domain.AnswerRating;
import network.foodsharing.quiz.domain.Quiz;
import network.foodsharing.quiz.domain.QuizAction;
import network.foodsharing.quiz.domain.QuizAnswer;
import network.foodsharing.quiz.domain.QuizAnswerI18n;
import network.foodsharing.quiz.domain.QuizI18n;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizQuestion;
import network.foodsharing.quiz.domain.QuizQuestionComment;
import network.foodsharing.quiz.domain.QuizQuestionI18n;
import network.foodsharing.quiz.repository.QuizAnswerI18nRepository;
import network.foodsharing.quiz.repository.QuizAnswerRepository;
import network.foodsharing.quiz.repository.QuizI18nRepository;
import network.foodsharing.quiz.repository.QuizQuestionCommentRepository;
import network.foodsharing.quiz.repository.QuizQuestionI18nRepository;
import network.foodsharing.quiz.repository.QuizQuestionRepository;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quiz editor (plan §13.9 Q3). Editors of a quiz are ORGA and the admins of a working group whose {@code quiz_kinds}
 * contains the quiz (decision: editor scope via groups); readers are additionally the members of such a group, except
 * for the role quizzes (FOODSAVER, STORE_MANAGER, AMBASSADOR) whose questions only editors see. Texts are maps
 * language → text (A14); a blank text removes that language.
 */
@Service
public class QuizEditorService {

    public record QuizDetailView(Quiz quiz, Map<String, String> names, Map<String, String> descriptions,
                                 int questionCount, boolean mayEdit) {
    }

    public record AnswerView(QuizAnswer answer, Map<String, String> texts, Map<String, String> explanations) {
    }

    public record QuestionView(QuizQuestion question, Map<String, String> texts, List<AnswerView> answers,
                               long commentCount) {
    }

    public record QuizInput(Map<String, String> names, Map<String, String> descriptions, Integer maxFailurePoints,
                            Integer questionCountTimed, Integer questionCountUntimed, String reason) {
    }

    public record QuestionInput(Map<String, String> texts, Integer durationSeconds, String wikiLink, Boolean mandatory,
                                Integer failurePoints) {
    }

    public record AnswerInput(Map<String, String> texts, Map<String, String> explanations, AnswerRating rating) {
    }

    public record CommentView(QuizQuestionComment comment, User author) {
    }

    private final QuizRepository quizzes;
    private final QuizI18nRepository quizTexts;
    private final QuizQuestionRepository questions;
    private final QuizQuestionI18nRepository questionTexts;
    private final QuizAnswerRepository answers;
    private final QuizAnswerI18nRepository answerTexts;
    private final QuizQuestionCommentRepository comments;
    private final RegionService regions;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final Clock clock;

    public QuizEditorService(QuizRepository quizzes, QuizI18nRepository quizTexts, QuizQuestionRepository questions,
                             QuizQuestionI18nRepository questionTexts, QuizAnswerRepository answers,
                             QuizAnswerI18nRepository answerTexts, QuizQuestionCommentRepository comments,
                             RegionService regions, UserService users, CurrentActorService currentActor,
                             PermissionService permissions, HistoryService history, Clock clock) {
        this.quizzes = quizzes;
        this.quizTexts = quizTexts;
        this.questions = questions;
        this.questionTexts = questionTexts;
        this.answers = answers;
        this.answerTexts = answerTexts;
        this.comments = comments;
        this.regions = regions;
        this.users = users;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.clock = clock;
    }

    // -------------------------------------------------------------------------------------- permissions

    public boolean mayEdit(SessionPrincipal actor, QuizKind kind) {
        if (permissions.isOrga(actor)) {
            return true;
        }
        return editorGroups(kind).stream().anyMatch(group -> permissions.isAmbassador(actor, group.getId()));
    }

    public boolean mayRead(SessionPrincipal actor, QuizKind kind) {
        return mayEdit(actor, kind) || (!QuizRuleService.ROLE_QUIZZES.contains(kind) && isEditorGroupMember(actor, kind));
    }

    public boolean isEditorGroupMember(SessionPrincipal actor, QuizKind kind) {
        return editorGroups(kind).stream().anyMatch(group -> permissions.isMemberOf(actor, group.getId())
                || permissions.isAmbassador(actor, group.getId()));
    }

    private List<Region> editorGroups(QuizKind kind) {
        return regions.groupsWithQuizKind(kind.name());
    }

    // -------------------------------------------------------------------------------------- quiz

    /** @throws ApiException 404 QUIZ_NOT_FOUND */
    public Quiz requireQuiz(QuizKind kind) {
        return quizzes.findByKind(kind).orElseThrow(() -> ApiException.notFound(ApiErrorCode.QUIZ_NOT_FOUND));
    }

    /** @throws ApiException 403 unless reader, 404 QUIZ_NOT_FOUND */
    @Transactional(readOnly = true)
    public QuizDetailView quiz(QuizKind kind) {
        Quiz quiz = requireQuiz(kind);
        SessionPrincipal actor = requireRead(kind);
        List<QuizI18n> texts = quizTexts.findByOwnerId(quiz.getId());
        return new QuizDetailView(quiz, texts.stream().collect(Collectors.toMap(QuizI18n::getLanguage, QuizI18n::getName)),
                texts.stream().filter(t -> t.getDescription() != null)
                        .collect(Collectors.toMap(QuizI18n::getLanguage, QuizI18n::getDescription)),
                questions.findByQuizIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(quiz.getId()).size(),
                mayEdit(actor, kind));
    }

    /** @throws ApiException 403 unless editor, 404 QUIZ_NOT_FOUND */
    @Transactional
    public QuizDetailView updateQuiz(QuizKind kind, QuizInput input) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        if (input.maxFailurePoints() != null) {
            quiz.setMaxFailurePoints(input.maxFailurePoints());
        }
        if (input.questionCountTimed() != null) {
            quiz.setQuestionCountTimed(input.questionCountTimed());
        }
        if (input.questionCountUntimed() != null) {
            quiz.setQuestionCountUntimed(input.questionCountUntimed());
        }
        if (input.names() != null || input.descriptions() != null) {
            Map<String, QuizI18n> existing = quizTexts.findByOwnerId(quiz.getId()).stream()
                    .collect(Collectors.toMap(QuizI18n::getLanguage, Function.identity()));
            if (input.names() != null) {
                input.names().forEach((language, name) -> {
                    QuizI18n row = existing.computeIfAbsent(language, l -> newQuizText(quiz.getId(), l));
                    row.setName(name);
                    quizTexts.save(row);
                });
            }
            if (input.descriptions() != null) {
                input.descriptions().forEach((language, description) -> {
                    QuizI18n row = existing.get(language);
                    if (row != null) {
                        row.setDescription(blankToNull(description));
                    }
                });
            }
        }
        history.record(history.event(QuizAction.QUIZ_UPDATED, quiz.getId()).reason(input.reason())
                .metadata(Map.of("maxFailurePoints", quiz.getMaxFailurePoints(),
                        "questionCountTimed", quiz.getQuestionCountTimed(),
                        "questionCountUntimed", quiz.getQuestionCountUntimed())).build());
        return quiz(kind);
    }

    // -------------------------------------------------------------------------------------- questions

    /** @throws ApiException 403 unless reader, 404 QUIZ_NOT_FOUND */
    @Transactional(readOnly = true)
    public List<QuestionView> questions(QuizKind kind) {
        Quiz quiz = requireQuiz(kind);
        requireRead(kind);
        return views(questions.findByQuizIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(quiz.getId()));
    }

    /** All non-deleted questions with answers (for sessions; no permission check). */
    @Transactional(readOnly = true)
    public List<QuestionView> pool(Quiz quiz) {
        return views(questions.findByQuizIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(quiz.getId()));
    }

    @Transactional
    public QuestionView createQuestion(QuizKind kind, QuestionInput input) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        if (input.texts() == null || input.texts().values().stream().allMatch(text -> text == null || text.isBlank())) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "texts");
        }
        QuizQuestion question = new QuizQuestion();
        question.setQuizId(quiz.getId());
        apply(question, input);
        QuizQuestion saved = questions.saveAndFlush(question);
        replaceQuestionTexts(saved.getId(), input.texts());
        history.record(history.event(QuizAction.QUESTION_CREATED, quiz.getId())
                .metadata("questionId", saved.getId().toString()).build());
        return view(saved);
    }

    @Transactional
    public QuestionView updateQuestion(QuizKind kind, UUID questionId, QuestionInput input) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        QuizQuestion question = requireQuestion(quiz, questionId);
        apply(question, input);
        if (input.texts() != null) {
            replaceQuestionTexts(question.getId(), input.texts());
        }
        history.record(history.event(QuizAction.QUESTION_UPDATED, quiz.getId())
                .metadata("questionId", questionId.toString()).build());
        return view(question);
    }

    @Transactional
    public void deleteQuestion(QuizKind kind, UUID questionId) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        QuizQuestion question = requireQuestion(quiz, questionId);
        question.setDeletedAt(clock.instant());
        history.record(history.event(QuizAction.QUESTION_DELETED, quiz.getId())
                .metadata("questionId", questionId.toString()).build());
    }

    // -------------------------------------------------------------------------------------- answers

    @Transactional
    public AnswerView createAnswer(QuizKind kind, UUID questionId, AnswerInput input) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        QuizQuestion question = requireQuestion(quiz, questionId);
        if (input.texts() == null || input.texts().isEmpty() || input.rating() == null) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        QuizAnswer answer = new QuizAnswer();
        answer.setQuestionId(question.getId());
        answer.setRating(input.rating());
        QuizAnswer saved = answers.saveAndFlush(answer);
        replaceAnswerTexts(saved.getId(), input.texts(), input.explanations());
        history.record(history.event(QuizAction.ANSWER_CREATED, quiz.getId())
                .metadata(Map.of("questionId", questionId.toString(), "answerId", saved.getId().toString())).build());
        return answerView(saved);
    }

    @Transactional
    public AnswerView updateAnswer(QuizKind kind, UUID questionId, UUID answerId, AnswerInput input) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        QuizAnswer answer = requireAnswer(requireQuestion(quiz, questionId), answerId);
        if (input.rating() != null) {
            answer.setRating(input.rating());
        }
        if (input.texts() != null || input.explanations() != null) {
            replaceAnswerTexts(answer.getId(), input.texts(), input.explanations());
        }
        history.record(history.event(QuizAction.ANSWER_UPDATED, quiz.getId())
                .metadata(Map.of("questionId", questionId.toString(), "answerId", answerId.toString())).build());
        return answerView(answer);
    }

    @Transactional
    public void deleteAnswer(QuizKind kind, UUID questionId, UUID answerId) {
        Quiz quiz = requireQuiz(kind);
        requireEdit(kind);
        QuizAnswer answer = requireAnswer(requireQuestion(quiz, questionId), answerId);
        answer.setDeletedAt(clock.instant());
        history.record(history.event(QuizAction.ANSWER_DELETED, quiz.getId())
                .metadata(Map.of("questionId", questionId.toString(), "answerId", answerId.toString())).build());
    }

    // -------------------------------------------------------------------------------------- comments

    @Transactional(readOnly = true)
    public List<CommentView> comments(QuizKind kind, UUID questionId) {
        Quiz quiz = requireQuiz(kind);
        requireRead(kind);
        requireQuestion(quiz, questionId);
        List<QuizQuestionComment> rows = comments.findByQuestionIdOrderByCreatedAtAscIdAsc(questionId);
        Map<UUID, User> authors = users.findActive(rows.stream().map(QuizQuestionComment::getAuthorId)
                        .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream().map(row -> new CommentView(row, authors.get(row.getAuthorId()))).toList();
    }

    @Transactional
    public CommentView addComment(QuizKind kind, UUID questionId, String text) {
        Quiz quiz = requireQuiz(kind);
        SessionPrincipal actor = requireRead(kind);
        requireQuestion(quiz, questionId);
        QuizQuestionComment comment = new QuizQuestionComment();
        comment.setQuestionId(questionId);
        comment.setAuthorId(actor.userId());
        comment.setText(text.strip());
        QuizQuestionComment saved = comments.save(comment);
        history.record(history.event(QuizAction.COMMENT_ADDED, quiz.getId())
                .metadata("questionId", questionId.toString()).build());
        return new CommentView(saved, users.findActive(actor.userId()).orElse(null));
    }

    // -------------------------------------------------------------------------------------- helpers

    private List<QuestionView> views(List<QuizQuestion> rows) {
        List<UUID> ids = rows.stream().map(QuizQuestion::getId).toList();
        Map<UUID, Map<String, String>> texts = questionTexts.findByOwnerIdIn(ids).stream()
                .collect(Collectors.groupingBy(QuizQuestionI18n::getOwnerId,
                        Collectors.toMap(QuizQuestionI18n::getLanguage, QuizQuestionI18n::getText)));
        Map<UUID, List<QuizAnswer>> byQuestion = ids.isEmpty() ? Map.of()
                : answers.findByQuestionIdInAndDeletedAtIsNull(ids).stream()
                .sorted(java.util.Comparator.comparing(QuizAnswer::getCreatedAt))
                .collect(Collectors.groupingBy(QuizAnswer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
        List<UUID> answerIds = byQuestion.values().stream().flatMap(List::stream).map(QuizAnswer::getId).toList();
        Map<UUID, List<QuizAnswerI18n>> answerTextsById = answerIds.isEmpty() ? Map.of()
                : answerTexts.findByOwnerIdIn(answerIds).stream().collect(Collectors.groupingBy(QuizAnswerI18n::getOwnerId));
        List<QuestionView> result = new ArrayList<>();
        for (QuizQuestion row : rows) {
            List<AnswerView> answerViews = byQuestion.getOrDefault(row.getId(), List.of()).stream()
                    .map(answer -> answerView(answer, answerTextsById.getOrDefault(answer.getId(), List.of()))).toList();
            result.add(new QuestionView(row, texts.getOrDefault(row.getId(), Map.of()), answerViews,
                    comments.countByQuestionId(row.getId())));
        }
        return result;
    }

    private QuestionView view(QuizQuestion question) {
        return views(List.of(question)).getFirst();
    }

    private AnswerView answerView(QuizAnswer answer) {
        return answerView(answer, answerTexts.findByOwnerId(answer.getId()));
    }

    private static AnswerView answerView(QuizAnswer answer, List<QuizAnswerI18n> texts) {
        return new AnswerView(answer,
                texts.stream().collect(Collectors.toMap(QuizAnswerI18n::getLanguage, QuizAnswerI18n::getText)),
                texts.stream().filter(t -> t.getExplanation() != null)
                        .collect(Collectors.toMap(QuizAnswerI18n::getLanguage, QuizAnswerI18n::getExplanation)));
    }

    private static void apply(QuizQuestion question, QuestionInput input) {
        if (input.durationSeconds() != null) {
            question.setDurationSeconds(input.durationSeconds());
        }
        if (input.wikiLink() != null) {
            question.setWikiLink(blankToNull(input.wikiLink()));
        }
        if (input.mandatory() != null) {
            question.setMandatory(input.mandatory());
        }
        if (input.failurePoints() != null) {
            question.setFailurePoints(input.failurePoints());
        }
    }

    private void replaceQuestionTexts(UUID questionId, Map<String, String> texts) {
        Map<String, QuizQuestionI18n> existing = questionTexts.findByOwnerId(questionId).stream()
                .collect(Collectors.toMap(QuizQuestionI18n::getLanguage, Function.identity()));
        texts.forEach((language, text) -> {
            QuizQuestionI18n row = existing.get(language);
            if (text == null || text.isBlank()) {
                if (row != null) {
                    questionTexts.delete(row);
                }
                return;
            }
            if (row == null) {
                row = new QuizQuestionI18n();
                row.setOwnerId(questionId);
                row.setLanguage(language);
            }
            row.setText(text.strip());
            questionTexts.save(row);
        });
    }

    private void replaceAnswerTexts(UUID answerId, Map<String, String> texts, Map<String, String> explanations) {
        Map<String, QuizAnswerI18n> existing = answerTexts.findByOwnerId(answerId).stream()
                .collect(Collectors.toMap(QuizAnswerI18n::getLanguage, Function.identity()));
        if (texts != null) {
            texts.forEach((language, text) -> {
                QuizAnswerI18n row = existing.get(language);
                if (text == null || text.isBlank()) {
                    if (row != null) {
                        answerTexts.delete(row);
                        existing.remove(language);
                    }
                    return;
                }
                if (row == null) {
                    row = new QuizAnswerI18n();
                    row.setOwnerId(answerId);
                    row.setLanguage(language);
                    existing.put(language, row);
                }
                row.setText(text.strip());
                answerTexts.save(row);
            });
        }
        if (explanations != null) {
            explanations.forEach((language, explanation) -> {
                QuizAnswerI18n row = existing.get(language);
                if (row != null) {
                    row.setExplanation(blankToNull(explanation));
                    answerTexts.save(row);
                }
            });
        }
    }

    private QuizQuestion requireQuestion(Quiz quiz, UUID questionId) {
        return questions.findByIdAndQuizIdAndDeletedAtIsNull(questionId, quiz.getId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.QUESTION_NOT_FOUND));
    }

    private QuizAnswer requireAnswer(QuizQuestion question, UUID answerId) {
        return answers.findByIdAndQuestionIdAndDeletedAtIsNull(answerId, question.getId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.ANSWER_NOT_FOUND));
    }

    private SessionPrincipal requireRead(QuizKind kind) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!mayRead(actor, kind)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    private SessionPrincipal requireEdit(QuizKind kind) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!mayEdit(actor, kind)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    private static QuizI18n newQuizText(UUID quizId, String language) {
        QuizI18n row = new QuizI18n();
        row.setOwnerId(quizId);
        row.setLanguage(language);
        return row;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
