package network.foodsharing.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.StoreVisibility;
import network.foodsharing.survey.domain.Survey;
import network.foodsharing.survey.domain.SurveyQuestionType;
import network.foodsharing.survey.repository.SurveyAnswerRepository;
import network.foodsharing.survey.repository.SurveyCoauthorRepository;
import network.foodsharing.survey.repository.SurveyQuestionRepository;
import network.foodsharing.survey.repository.SurveyRepository;
import network.foodsharing.survey.repository.SurveyResponseRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserSearchService;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/** SV1 (formerly SurveyFlowIT): co-authors must be allowed to manage surveys and are told by a SURVEY_SHARED bell. */
class SurveyCoauthorTest {

    static final UUID OWNER = UUID.randomUUID();
    static final UUID MANAGER = UUID.randomUUID();
    static final UUID MEMBER = UUID.randomUUID();
    static final List<SurveyService.QuestionInput> QUESTIONS = List.of(
            new SurveyService.QuestionInput(null, SurveyQuestionType.TEXT, "Frage", true, List.of()));

    final SurveyRepository surveys = mock(SurveyRepository.class);
    final PermissionService permissions = mock(PermissionService.class);
    final CurrentActorService currentActor = mock(CurrentActorService.class);
    final UserService users = mock(UserService.class);
    final NotificationService notifications = mock(NotificationService.class);
    final HistoryService history = mock(HistoryService.class);
    SurveyService service;

    @BeforeEach
    void setUp() {
        SessionPrincipal owner = new SessionPrincipal(OWNER, UUID.randomUUID(), Role.AMBASSADOR, "bot@x");
        when(currentActor.requirePrincipal()).thenReturn(owner);
        when(currentActor.requireUserId()).thenReturn(OWNER);
        when(permissions.hasAnywhere(argThat(p -> p != null && !p.userId().equals(MEMBER)),
                eq(Permission.CAN_MANAGE_SURVEYS))).thenReturn(true);
        when(users.findActive(org.mockito.ArgumentMatchers.<Collection<UUID>>any())).thenAnswer(call ->
                ((Collection<UUID>) call.getArgument(0)).stream().map(SurveyCoauthorTest::user).toList());
        when(users.findActive(OWNER)).thenReturn(Optional.of(user(OWNER)));
        when(surveys.save(any())).thenAnswer(call -> {
            Survey survey = call.getArgument(0);
            survey.setId(UUID.randomUUID());
            return survey;
        });
        when(history.event(any(), any())).thenAnswer(invocation ->
                HistoryEvent.builder(invocation.getArgument(0), invocation.getArgument(1)));
        service = new SurveyService(surveys, mock(SurveyCoauthorRepository.class), mock(SurveyQuestionRepository.class),
                mock(SurveyResponseRepository.class), mock(SurveyAnswerRepository.class), permissions, currentActor,
                users, mock(UserSearchService.class), mock(StoreService.class), mock(StoreVisibility.class),
                notifications, history, new ObjectMapper());
    }

    static User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setRole(Role.FOODSAVER);
        user.setDisplayName("U");
        return user;
    }

    @Test
    void aCoauthorWithoutTheSurveyPermissionIsRefused() {
        assertThatThrownBy(() -> service.create("Neue Umfrage", null, QUESTIONS, List.of(MEMBER)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED));
        verify(surveys, never()).save(any());
        verify(notifications, never()).notify(any());
    }

    @Test
    void anAddedCoauthorIsToldByBell() {
        service.create("Neue Umfrage", null, QUESTIONS, List.of(MANAGER, OWNER));
        ArgumentCaptor<NotificationRequest> bell = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(bell.capture());
        assertThat(bell.getValue().type()).isEqualTo(NotificationType.SURVEY_SHARED);
        assertThat(bell.getValue().recipientIds()).isEqualTo(Set.of(MANAGER));
    }
}
