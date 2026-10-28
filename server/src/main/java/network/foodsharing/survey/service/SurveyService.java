package network.foodsharing.survey.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.survey.domain.Survey;
import network.foodsharing.survey.domain.SurveyAction;
import network.foodsharing.survey.domain.SurveyAnswer;
import network.foodsharing.survey.domain.SurveyCoauthor;
import network.foodsharing.survey.domain.SurveyQuestion;
import network.foodsharing.survey.domain.SurveyQuestionType;
import network.foodsharing.survey.domain.SurveyResponse;
import network.foodsharing.survey.repository.SurveyAnswerRepository;
import network.foodsharing.survey.repository.SurveyCoauthorRepository;
import network.foodsharing.survey.repository.SurveyQuestionRepository;
import network.foodsharing.survey.repository.SurveyRepository;
import network.foodsharing.survey.repository.SurveyResponseRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserSearchService;
import network.foodsharing.user.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Surveys (SV1, port of the reference einab-tabelle {@code SurveyService}, A36): create / edit / results / delete for
 * the owner and co-authors (both need {@code CAN_MANAGE_SURVEYS} somewhere; delete = owner), fill once by any logged-in
 * user. STORE questions are answered only through the link's {@code storeId}.
 */
@Service
public class SurveyService {

    static final int MAX_ANSWER_LENGTH = 4000;
    static final int MAX_COAUTHORS = 50;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    public record QuestionInput(UUID id, SurveyQuestionType type, String label, boolean required, List<String> options) {
    }

    public record AnswerInput(UUID questionId, List<String> values) {
    }

    public record SurveyView(Survey survey, List<SurveyQuestion> questions, long responseCount, long questionCount,
                             List<UUID> coauthorIds, boolean owner) {
    }

    public record ResultRow(UUID userId, java.time.Instant submittedAt, Store store, Map<UUID, List<String>> answers) {
    }

    public record ResultsView(List<SurveyQuestion> questions, List<ResultRow> rows) {
    }

    public record FormView(Survey survey, List<SurveyQuestion> questions, boolean alreadySubmitted, Store store) {
    }

    private final SurveyRepository surveys;
    private final SurveyCoauthorRepository coauthors;
    private final SurveyQuestionRepository questions;
    private final SurveyResponseRepository responses;
    private final SurveyAnswerRepository answers;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final UserService users;
    private final UserSearchService userSearch;
    private final StoreService stores;
    private final NotificationService notifications;
    private final HistoryService history;
    private final ObjectMapper json;

    public SurveyService(SurveyRepository surveys, SurveyCoauthorRepository coauthors,
                         SurveyQuestionRepository questions, SurveyResponseRepository responses,
                         SurveyAnswerRepository answers, PermissionService permissions,
                         CurrentActorService currentActor, UserService users, UserSearchService userSearch,
                         StoreService stores, NotificationService notifications, HistoryService history,
                         ObjectMapper json) {
        this.surveys = surveys;
        this.coauthors = coauthors;
        this.questions = questions;
        this.responses = responses;
        this.answers = answers;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.users = users;
        this.userSearch = userSearch;
        this.stores = stores;
        this.notifications = notifications;
        this.history = history;
        this.json = json;
    }

    /** Own and co-authored surveys. @throws ApiException 403 without CAN_MANAGE_SURVEYS */
    @Transactional(readOnly = true)
    public List<SurveyView> list() {
        UUID me = requireManager();
        List<Survey> list = surveys.listAccessible(me);
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Survey::getId).toList();
        Map<UUID, Long> responseCounts = counts(responses.countBySurveys(ids));
        Map<UUID, Long> questionCounts = counts(questions.countBySurveys(ids));
        return list.stream().map(s -> new SurveyView(s, List.of(), responseCounts.getOrDefault(s.getId(), 0L),
                questionCounts.getOrDefault(s.getId(), 0L), List.of(), s.getOwnerId().equals(me))).toList();
    }

    /** Active users with CAN_MANAGE_SURVEYS matching {@code q} (name prefix), except me. */
    @Transactional(readOnly = true)
    public List<User> coauthorCandidates(String q) {
        UUID me = requireManager();
        return userSearch.search(q == null ? "" : q, null, 20).stream()
                .filter(u -> !u.getId().equals(me) && mayManage(u)).toList();
    }

    /** @throws ApiException 403, 404 SURVEY_NOT_FOUND (also for surveys of others) */
    @Transactional(readOnly = true)
    public SurveyView get(UUID surveyId) {
        UUID me = requireManager();
        return view(requireAccessible(surveyId, me), me);
    }

    /** @throws ApiException 403, 400 VALIDATION_FAILED, 404 USER_NOT_FOUND (co-author) */
    @Transactional
    public SurveyView create(String title, String description, List<QuestionInput> input, List<UUID> coauthorIds) {
        UUID me = requireManager();
        validateDefinition(title, input);
        List<UUID> newCoauthors = validateCoauthors(me, coauthorIds);
        Survey survey = new Survey();
        survey.setOwnerId(me);
        survey.setTitle(title.strip());
        survey.setDescription(blankToNull(description));
        survey = surveys.save(survey);
        for (int i = 0; i < input.size(); i++) {
            SurveyQuestion question = new SurveyQuestion();
            question.setSurveyId(survey.getId());
            apply(question, input.get(i), i);
            questions.save(question);
        }
        saveCoauthors(survey, newCoauthors);
        history.record(history.event(SurveyAction.CREATED, survey.getId()).metadata("questions", input.size())
                .build());
        return view(survey, me);
    }

    /**
     * Replaces title, description and questions, keeping the ids of questions given with an id (answers of removed
     * questions or of questions whose type changed are deleted). Co-authors are changed only by the owner (null =
     * unchanged); newly added ones get {@code SURVEY_SHARED}.
     *
     * @throws ApiException 403, 404 SURVEY_NOT_FOUND / USER_NOT_FOUND, 400 VALIDATION_FAILED
     */
    @Transactional
    public SurveyView update(UUID surveyId, String title, String description, List<QuestionInput> input,
                             List<UUID> coauthorIds) {
        UUID me = requireManager();
        Survey survey = requireAccessible(surveyId, me);
        boolean owner = survey.getOwnerId().equals(me);
        validateDefinition(title, input);
        List<UUID> newCoauthors = owner && coauthorIds != null ? validateCoauthors(me, coauthorIds) : null;
        survey.setTitle(title.strip());
        survey.setDescription(blankToNull(description));

        Map<UUID, SurveyQuestion> existing = new HashMap<>();
        questions.findBySurveyIdOrderBySortOrder(surveyId).forEach(q -> existing.put(q.getId(), q));
        Set<UUID> kept = new HashSet<>();
        for (QuestionInput q : input) {
            if (q.id() != null && (!existing.containsKey(q.id()) || !kept.add(q.id()))) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
        }
        for (SurveyQuestion question : existing.values()) {
            QuestionInput match = input.stream().filter(q -> question.getId().equals(q.id())).findFirst().orElse(null);
            if (match == null || match.type() != question.getType()) {
                answers.deleteByQuestion(question.getId());
            }
            if (match == null) {
                questions.delete(question);
            }
        }
        questions.flush();
        for (int i = 0; i < input.size(); i++) {
            QuestionInput q = input.get(i);
            SurveyQuestion question = q.id() == null ? new SurveyQuestion() : existing.get(q.id());
            question.setSurveyId(surveyId);
            apply(question, q, i);
            questions.save(question);
        }
        surveys.save(survey);
        if (newCoauthors != null) {
            Set<UUID> before = new HashSet<>(coauthorIdsOf(surveyId));
            coauthors.deleteBySurvey(surveyId);
            for (UUID userId : newCoauthors) {
                SurveyCoauthor coauthor = new SurveyCoauthor();
                coauthor.setSurveyId(surveyId);
                coauthor.setUserId(userId);
                coauthors.save(coauthor);
            }
            shared(survey, newCoauthors.stream().filter(id -> !before.contains(id)).toList());
        }
        history.record(history.event(SurveyAction.UPDATED, surveyId).metadata("questions", input.size()).build());
        return view(surveys.findById(surveyId).orElseThrow(), me);
    }

    /** Owner only; cascades to questions, co-authors and responses. @throws ApiException 403, 404 */
    @Transactional
    public void delete(UUID surveyId) {
        UUID me = requireManager();
        Survey survey = surveys.findById(surveyId).filter(s -> s.getOwnerId().equals(me))
                .orElseThrow(SurveyService::notFound);
        surveys.delete(survey);
        history.record(history.event(SurveyAction.DELETED, surveyId).metadata("title", survey.getTitle()).build());
    }

    /** Owner and co-authors. @throws ApiException 403, 404 */
    @Transactional(readOnly = true)
    public ResultsView results(UUID surveyId) {
        UUID me = requireManager();
        requireAccessible(surveyId, me);
        List<SurveyQuestion> list = questions.findBySurveyIdOrderBySortOrder(surveyId);
        List<SurveyResponse> all = responses.findBySurveyIdOrderByCreatedAt(surveyId);
        Map<UUID, Map<UUID, List<String>>> byResponse = new HashMap<>();
        if (!all.isEmpty()) {
            for (SurveyAnswer answer : answers.findByResponseIdIn(all.stream().map(SurveyResponse::getId).toList())) {
                byResponse.computeIfAbsent(answer.getResponseId(), k -> new HashMap<>())
                        .put(answer.getQuestionId(), readList(answer.getValuesJson()));
            }
        }
        Map<UUID, Store> storeById = new HashMap<>();
        List<ResultRow> rows = new ArrayList<>();
        for (SurveyResponse response : all) {
            Store store = response.getStoreId() == null ? null
                    : storeById.computeIfAbsent(response.getStoreId(), id -> findStore(id));
            rows.add(new ResultRow(response.getUserId(), response.getCreatedAt(), store,
                    byResponse.getOrDefault(response.getId(), Map.of())));
        }
        return new ResultsView(list, rows);
    }

    /**
     * The fill form for any logged-in user; with {@code storeId} the store is resolved for the STORE question.
     *
     * @throws ApiException 404 SURVEY_NOT_FOUND / STORE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public FormView form(UUID surveyId, UUID storeId) {
        UUID me = currentActor.requireUserId();
        Survey survey = surveys.findById(surveyId).orElseThrow(SurveyService::notFound);
        Store store = storeId == null ? null : stores.require(storeId);
        return new FormView(survey, questions.findBySurveyIdOrderBySortOrder(surveyId),
                responses.existsBySurveyIdAndUserId(surveyId, me), store);
    }

    /**
     * Submits once. The STORE question's answer is the link's store (409 STORE_ANSWER_LINK_ONLY without a link store
     * or when the answer names another store).
     *
     * @throws ApiException 404 SURVEY_NOT_FOUND / STORE_NOT_FOUND, 409 SURVEY_ALREADY_SUBMITTED /
     *                      STORE_ANSWER_LINK_ONLY, 400 SURVEY_QUESTION_REQUIRED / SURVEY_ANSWER_INVALID
     */
    @Transactional
    public void submit(UUID surveyId, UUID storeId, List<AnswerInput> input) {
        UUID me = currentActor.requireUserId();
        Survey survey = surveys.findById(surveyId).orElseThrow(SurveyService::notFound);
        if (responses.existsBySurveyIdAndUserId(surveyId, me)) {
            throw ApiException.conflict(ApiErrorCode.SURVEY_ALREADY_SUBMITTED);
        }
        List<SurveyQuestion> list = questions.findBySurveyIdOrderBySortOrder(surveyId);
        boolean hasStore = list.stream().anyMatch(q -> q.getType() == SurveyQuestionType.STORE);
        Store store = null;
        if (hasStore) {
            if (storeId == null) {
                throw ApiException.conflict(ApiErrorCode.STORE_ANSWER_LINK_ONLY);
            }
            store = stores.require(storeId);
        }
        Map<UUID, List<String>> cleaned = cleanAnswers(list, input == null ? List.of() : input,
                store == null ? null : store.getId().toString());
        SurveyResponse response = new SurveyResponse();
        response.setSurveyId(survey.getId());
        response.setUserId(me);
        response.setStoreId(store == null ? null : store.getId());
        try {
            response = responses.saveAndFlush(response);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(ApiErrorCode.SURVEY_ALREADY_SUBMITTED);
        }
        for (Map.Entry<UUID, List<String>> entry : cleaned.entrySet()) {
            SurveyAnswer answer = new SurveyAnswer();
            answer.setResponseId(response.getId());
            answer.setQuestionId(entry.getKey());
            answer.setValuesJson(json.writeValueAsString(entry.getValue()));
            answers.save(answer);
        }
        history.record(history.event(SurveyAction.RESPONSE_SUBMITTED, surveyId).build());
    }

    /** Checks every answer against its question; returns the answered questions only. */
    static Map<UUID, List<String>> cleanAnswers(List<SurveyQuestion> list, List<AnswerInput> input, String storeId,
                                                java.util.function.Function<String, List<String>> options) {
        Map<UUID, SurveyQuestion> byId = new HashMap<>();
        list.forEach(q -> byId.put(q.getId(), q));
        Map<UUID, List<String>> given = new HashMap<>();
        for (AnswerInput answer : input) {
            if (answer.questionId() == null || !byId.containsKey(answer.questionId())
                    || given.containsKey(answer.questionId())) {
                throw ApiException.badRequest(ApiErrorCode.SURVEY_ANSWER_INVALID);
            }
            List<String> values = new ArrayList<>();
            if (answer.values() != null) {
                answer.values().stream().filter(v -> v != null && !v.isBlank()).map(String::strip).forEach(values::add);
            }
            given.put(answer.questionId(), values);
        }
        Map<UUID, List<String>> cleaned = new HashMap<>();
        for (SurveyQuestion question : list) {
            List<String> values = given.getOrDefault(question.getId(), List.of());
            if (question.getType() == SurveyQuestionType.STORE) {
                if (!values.isEmpty() && !values.equals(List.of(storeId))) {
                    throw ApiException.conflict(ApiErrorCode.STORE_ANSWER_LINK_ONLY);
                }
                values = List.of(storeId);
            }
            if (values.isEmpty()) {
                if (question.isRequired()) {
                    throw ApiException.badRequest(ApiErrorCode.SURVEY_QUESTION_REQUIRED);
                }
                continue;
            }
            List<String> allowed = options.apply(question.getOptionsJson());
            boolean valid = switch (question.getType()) {
                case TEXT -> values.size() == 1 && values.getFirst().length() <= MAX_ANSWER_LENGTH;
                case STORE -> true;
                case BOOLEAN -> values.size() == 1 && (values.getFirst().equals("true") || values.getFirst().equals("false"));
                case SINGLE_CHOICE -> values.size() == 1 && allowed.contains(values.getFirst());
                case MULTIPLE_CHOICE -> new HashSet<>(values).size() == values.size() && allowed.containsAll(values);
            };
            if (!valid) {
                throw ApiException.badRequest(ApiErrorCode.SURVEY_ANSWER_INVALID);
            }
            cleaned.put(question.getId(), values);
        }
        return cleaned;
    }

    private Map<UUID, List<String>> cleanAnswers(List<SurveyQuestion> list, List<AnswerInput> input, String storeId) {
        return cleanAnswers(list, input, storeId, this::readList);
    }

    /** Title 1–200, 1–100 questions with labels, at most one STORE question, choice questions ≥ 2 options. */
    static void validateDefinition(String title, List<QuestionInput> input) {
        if (title == null || title.isBlank() || title.strip().length() > 200 || input == null || input.isEmpty()
                || input.size() > 100) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        boolean store = false;
        for (QuestionInput q : input) {
            if (q.type() == null || q.label() == null || q.label().isBlank() || q.label().strip().length() > 500) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            if (q.type() == SurveyQuestionType.STORE) {
                if (store) {
                    throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
                }
                store = true;
            }
            if (isChoice(q.type()) && cleanOptions(q).size() < 2) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
        }
    }

    private void apply(SurveyQuestion question, QuestionInput input, int index) {
        question.setSortOrder(index);
        question.setType(input.type());
        question.setLabel(input.label().strip());
        // the store always comes from the link, so a STORE question is always answered
        question.setRequired(input.type() == SurveyQuestionType.STORE || input.required());
        question.setOptionsJson(json.writeValueAsString(isChoice(input.type()) ? cleanOptions(input) : List.of()));
    }

    static boolean isChoice(SurveyQuestionType type) {
        return type == SurveyQuestionType.SINGLE_CHOICE || type == SurveyQuestionType.MULTIPLE_CHOICE;
    }

    static List<String> cleanOptions(QuestionInput question) {
        Set<String> options = new LinkedHashSet<>();
        if (question.options() != null) {
            question.options().stream().filter(o -> o != null && !o.isBlank()).map(String::strip).forEach(options::add);
        }
        return List.copyOf(options);
    }

    /** Other active users holding CAN_MANAGE_SURVEYS; de-duplicated, the owner dropped. */
    private List<UUID> validateCoauthors(UUID owner, List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        Set<UUID> wanted = new LinkedHashSet<>(ids);
        wanted.remove(owner);
        wanted.remove(null);
        if (wanted.size() > MAX_COAUTHORS) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<User> found = users.findActive(wanted);
        if (found.size() != wanted.size()) {
            throw ApiException.notFound(ApiErrorCode.USER_NOT_FOUND);
        }
        if (!found.stream().allMatch(this::mayManage)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return List.copyOf(wanted);
    }

    private void saveCoauthors(Survey survey, List<UUID> ids) {
        for (UUID userId : ids) {
            SurveyCoauthor coauthor = new SurveyCoauthor();
            coauthor.setSurveyId(survey.getId());
            coauthor.setUserId(userId);
            coauthors.save(coauthor);
        }
        shared(survey, ids);
    }

    private void shared(Survey survey, List<UUID> added) {
        if (added.isEmpty()) {
            return;
        }
        String owner = users.findActive(survey.getOwnerId()).map(User::getDisplayName).orElse("");
        notifications.notify(NotificationRequest.to(added, NotificationType.SURVEY_SHARED)
                .param("title", survey.getTitle()).param("owner", owner).link("/surveys/" + survey.getId())
                .identifier("SURVEY_SHARED-" + survey.getId()).build());
    }

    private boolean mayManage(User user) {
        return permissions.hasAnywhere(new SessionPrincipal(user.getId(), null, user.getRole(), user.getEmail()),
                Permission.CAN_MANAGE_SURVEYS);
    }

    private List<UUID> coauthorIdsOf(UUID surveyId) {
        return coauthors.findBySurveyIdOrderByCreatedAt(surveyId).stream().map(SurveyCoauthor::getUserId).toList();
    }

    private SurveyView view(Survey survey, UUID me) {
        List<SurveyQuestion> list = questions.findBySurveyIdOrderBySortOrder(survey.getId());
        return new SurveyView(survey, list, responses.countBySurveyId(survey.getId()), list.size(),
                coauthorIdsOf(survey.getId()), survey.getOwnerId().equals(me));
    }

    private Store findStore(UUID storeId) {
        try {
            return stores.require(storeId);
        } catch (ApiException e) {
            return null;
        }
    }

    /** CAN_MANAGE_SURVEYS anywhere (ORGA always). @throws ApiException 403 ACCESS_DENIED */
    private UUID requireManager() {
        SessionPrincipal principal = currentActor.requirePrincipal();
        if (!permissions.hasAnywhere(principal, Permission.CAN_MANAGE_SURVEYS)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return principal.userId();
    }

    /** Owner or co-author; others' surveys answer 404 so their existence is not revealed. */
    private Survey requireAccessible(UUID surveyId, UUID me) {
        return surveys.findById(surveyId)
                .filter(s -> s.getOwnerId().equals(me) || coauthors.existsBySurveyIdAndUserId(surveyId, me))
                .orElseThrow(SurveyService::notFound);
    }

    /** May the user read the survey's history (owner / co-author)? */
    @Transactional(readOnly = true)
    public boolean isOwnerOrCoauthor(UUID surveyId, UUID userId) {
        return surveys.findById(surveyId).map(s -> s.getOwnerId().equals(userId)
                || coauthors.existsBySurveyIdAndUserId(surveyId, userId)).orElse(false);
    }

    List<String> readList(String value) {
        return value == null ? List.of() : json.readValue(value, STRING_LIST);
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        rows.forEach(r -> result.put((UUID) r[0], ((Number) r[1]).longValue()));
        return result;
    }

    private static ApiException notFound() {
        return ApiException.notFound(ApiErrorCode.SURVEY_NOT_FOUND);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
