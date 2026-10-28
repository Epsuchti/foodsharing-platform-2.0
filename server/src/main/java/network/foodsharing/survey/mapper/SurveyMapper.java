package network.foodsharing.survey.mapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.StoreRef;
import network.foodsharing.api.model.Survey;
import network.foodsharing.api.model.SurveyForm;
import network.foodsharing.api.model.SurveyQuestion;
import network.foodsharing.api.model.SurveyQuestionType;
import network.foodsharing.api.model.SurveyResultRow;
import network.foodsharing.api.model.SurveyResults;
import network.foodsharing.api.model.SurveySummary;
import network.foodsharing.api.model.SurveyWrite;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.store.domain.Store;
import network.foodsharing.survey.service.SurveyService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Survey entities and API models. */
@Component
public class SurveyMapper {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserService users;
    private final UserMapper userMapper;
    private final ObjectMapper json;

    public SurveyMapper(UserService users, UserMapper userMapper, ObjectMapper json) {
        this.users = users;
        this.userMapper = userMapper;
        this.json = json;
    }

    public static List<SurveyService.QuestionInput> toInputs(SurveyWrite write) {
        return write.getQuestions().stream().map(q -> new SurveyService.QuestionInput(q.getId(),
                network.foodsharing.survey.domain.SurveyQuestionType.valueOf(q.getType().getValue()), q.getLabel(),
                Boolean.TRUE.equals(q.getRequired()), q.getOptions())).toList();
    }

    public static List<SurveyService.AnswerInput> toAnswers(List<network.foodsharing.api.model.SurveyAnswer> answers) {
        return answers == null ? List.of() : answers.stream()
                .map(a -> new SurveyService.AnswerInput(a.getQuestionId(), a.getValues())).toList();
    }

    public List<SurveySummary> toSummaries(List<SurveyService.SurveyView> views) {
        Map<UUID, UserSummary> people = summaries(views.stream().map(v -> v.survey().getOwnerId()).toList());
        return views.stream().map(v -> new SurveySummary().id(v.survey().getId()).title(v.survey().getTitle())
                .owner(people.get(v.survey().getOwnerId())).createdAt(TimeMapper.toOffset(v.survey().getCreatedAt()))
                .updatedAt(TimeMapper.toOffset(v.survey().getUpdatedAt())).responseCount((int) v.responseCount())
                .questionCount((int) v.questionCount())).toList();
    }

    public Survey toSurvey(SurveyService.SurveyView view) {
        Set<UUID> ids = new HashSet<>(view.coauthorIds());
        ids.add(view.survey().getOwnerId());
        Map<UUID, UserSummary> people = summaries(ids);
        Survey result = new Survey().id(view.survey().getId()).title(view.survey().getTitle())
                .description(view.survey().getDescription()).owner(people.get(view.survey().getOwnerId()))
                .createdAt(TimeMapper.toOffset(view.survey().getCreatedAt()))
                .updatedAt(TimeMapper.toOffset(view.survey().getUpdatedAt()))
                .responseCount((int) view.responseCount()).mayDelete(view.owner());
        result.setQuestions(toQuestions(view.questions()));
        result.setCoauthors(view.coauthorIds().stream().map(people::get).filter(java.util.Objects::nonNull).toList());
        return result;
    }

    public SurveyResults toResults(SurveyService.ResultsView view) {
        Map<UUID, UserSummary> people = summaries(view.rows().stream().map(SurveyService.ResultRow::userId).toList());
        List<SurveyResultRow> rows = view.rows().stream().map(r -> {
            Map<String, List<String>> answers = new LinkedHashMap<>();
            r.answers().forEach((questionId, values) -> answers.put(questionId.toString(), values));
            return new SurveyResultRow().user(people.get(r.userId())).submittedAt(TimeMapper.toOffset(r.submittedAt()))
                    .store(toStoreRef(r.store())).answers(answers);
        }).toList();
        return new SurveyResults().questions(toQuestions(view.questions())).rows(rows).responseCount(rows.size());
    }

    public SurveyForm toForm(SurveyService.FormView view) {
        return new SurveyForm().id(view.survey().getId()).title(view.survey().getTitle())
                .description(view.survey().getDescription()).questions(toQuestions(view.questions()))
                .alreadySubmitted(view.alreadySubmitted()).store(toStoreRef(view.store()));
    }

    public List<UserSummary> toUsers(List<User> list) {
        return list.stream().map(userMapper::toSummary).toList();
    }

    private List<SurveyQuestion> toQuestions(List<network.foodsharing.survey.domain.SurveyQuestion> list) {
        return list.stream().map(q -> new SurveyQuestion().id(q.getId())
                .type(SurveyQuestionType.fromValue(q.getType().name())).label(q.getLabel()).required(q.isRequired())
                .options(json.readValue(q.getOptionsJson(), STRING_LIST))).toList();
    }

    private static StoreRef toStoreRef(Store store) {
        if (store == null) {
            return null;
        }
        return new StoreRef().id(store.getId()).name(store.getName()).regionId(store.getRegionId());
    }

    private Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAll(new HashSet<>(ids)).stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a, LinkedHashMap::new));
    }
}
