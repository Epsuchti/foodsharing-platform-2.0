package network.foodsharing.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreVisibility;
import network.foodsharing.survey.domain.Survey;
import network.foodsharing.survey.repository.SurveyAnswerRepository;
import network.foodsharing.survey.repository.SurveyCoauthorRepository;
import network.foodsharing.survey.repository.SurveyQuestionRepository;
import network.foodsharing.survey.repository.SurveyRepository;
import network.foodsharing.survey.repository.SurveyResponseRepository;
import network.foodsharing.user.service.UserSearchService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * SV1 access (e2e sv1-surveys "co-author sees results; others are forbidden; delete by owner", decision 180): results
 * for the owner and the co-authors, others' surveys answer 404 and people without the survey permission 403; only the
 * owner deletes; a respondent answers once.
 */
class SurveyServiceAccessTest {

    static final UUID SURVEY = UUID.randomUUID();
    static final UUID OWNER = UUID.randomUUID();
    static final UUID COAUTHOR = UUID.randomUUID();
    static final UUID OTHER_MANAGER = UUID.randomUUID();

    final SurveyRepository surveys = mock(SurveyRepository.class);
    final SurveyCoauthorRepository coauthors = mock(SurveyCoauthorRepository.class);
    final SurveyQuestionRepository questions = mock(SurveyQuestionRepository.class);
    final SurveyResponseRepository responses = mock(SurveyResponseRepository.class);
    final PermissionService permissions = mock(PermissionService.class);
    final CurrentActorService currentActor = mock(CurrentActorService.class);
    final HistoryService history = mock(HistoryService.class);
    SurveyService service;
    Survey survey;

    @BeforeEach
    void setUp() {
        survey = new Survey();
        survey.setId(SURVEY);
        survey.setOwnerId(OWNER);
        survey.setTitle("Zufriedenheit");
        when(surveys.findById(SURVEY)).thenReturn(Optional.of(survey));
        when(coauthors.existsBySurveyIdAndUserId(SURVEY, COAUTHOR)).thenReturn(true);
        when(questions.findBySurveyIdOrderBySortOrderAscIdAsc(SURVEY)).thenReturn(List.of());
        when(responses.findBySurveyIdOrderByCreatedAtAscIdAsc(SURVEY)).thenReturn(List.of());
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        service = new SurveyService(surveys, coauthors, questions, responses, mock(SurveyAnswerRepository.class),
                permissions, currentActor, mock(UserService.class), mock(UserSearchService.class),
                mock(StoreService.class), mock(StoreVisibility.class), mock(NotificationService.class), history,
                new ObjectMapper());
    }

    @Test
    void ownerAndCoauthorsReadTheResultsOtherManagersGetNotFound() {
        actAs(OWNER, true);
        assertThat(service.results(SURVEY).rows()).isEmpty();
        actAs(COAUTHOR, true);
        assertThat(service.results(SURVEY).rows()).isEmpty();
        actAs(OTHER_MANAGER, true);
        assertCode(() -> service.results(SURVEY), ApiErrorCode.SURVEY_NOT_FOUND);
    }

    @Test
    void withoutTheSurveyPermissionResultsAreForbidden() {
        actAs(COAUTHOR, false);
        assertCode(() -> service.results(SURVEY), ApiErrorCode.ACCESS_DENIED);
    }

    @Test
    void onlyTheOwnerDeletes() {
        actAs(COAUTHOR, true);
        assertCode(() -> service.delete(SURVEY), ApiErrorCode.SURVEY_NOT_FOUND);
        verify(surveys, never()).delete(any());
        actAs(OWNER, true);
        service.delete(SURVEY);
        verify(surveys).delete(survey);
    }

    @Test
    void aRespondentAnswersOnce() {
        actAs(OTHER_MANAGER, false);
        when(responses.existsBySurveyIdAndUserId(SURVEY, OTHER_MANAGER)).thenReturn(true);
        assertCode(() -> service.submit(SURVEY, null, List.of()), ApiErrorCode.SURVEY_ALREADY_SUBMITTED);
        verify(responses, never()).saveAndFlush(any());
    }

    private void actAs(UUID userId, boolean mayManage) {
        SessionPrincipal principal = new SessionPrincipal(userId, UUID.randomUUID(), Role.FOODSAVER, "u@x");
        when(currentActor.requirePrincipal()).thenReturn(principal);
        when(currentActor.requireUserId()).thenReturn(userId);
        when(permissions.hasAnywhere(eq(principal), eq(Permission.CAN_MANAGE_SURVEYS))).thenReturn(mayManage);
    }

    private static void assertCode(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(code));
    }
}
