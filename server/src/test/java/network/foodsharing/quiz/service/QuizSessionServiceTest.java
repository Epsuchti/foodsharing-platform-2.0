package network.foodsharing.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.quiz.domain.Quiz;
import network.foodsharing.quiz.domain.QuizKind;
import network.foodsharing.quiz.domain.QuizSession;
import network.foodsharing.quiz.domain.QuizSessionStatus;
import network.foodsharing.quiz.repository.QuizI18nRepository;
import network.foodsharing.quiz.repository.QuizRepository;
import network.foodsharing.quiz.repository.QuizSessionRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

/**
 * Q2 / A8: the confirmation of a passed role quiz and who reads someone's quiz sessions. Practice (test) sessions
 * never count, so a practice pass confirms nothing and leaves the role alone; the store manager confirmation needs
 * the current confidentiality notice (Ve1); quiz sessions are read by the user, quiz editors and whoever administrates
 * the user, nobody else.
 */
class QuizSessionServiceTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    private final QuizRepository quizzes = mock(QuizRepository.class);
    private final QuizSessionRepository sessions = mock(QuizSessionRepository.class);
    private final QuizEditorService editor = mock(QuizEditorService.class);
    private final UserService users = mock(UserService.class);
    private final AuthSessionService authSessions = mock(AuthSessionService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<QuizConsentChecker> consents = mock(ObjectProvider.class);
    private final QuizConsentChecker consentChecker = mock(QuizConsentChecker.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class, RETURNS_DEEP_STUBS);
    private QuizSessionService service;

    private final List<QuizSession> stored = new ArrayList<>();
    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new QuizSessionService(quizzes, mock(QuizI18nRepository.class), sessions, editor,
                new QuizRuleService(new AppProperties()), users, authSessions, consents, mock(ObjectProvider.class),
                currentActor, permissions, history, mock(ApplicationEventPublisher.class), new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestUsers.user("lea@test.local", Role.FOODSHARER);
        user.setId(UUID.randomUUID());
        when(users.requireActive(user.getId())).thenReturn(user);
        when(users.require(any())).thenReturn(user);
        when(consents.getIfAvailable()).thenReturn(consentChecker);
        login(user.getId(), Role.FOODSHARER);
    }

    private void login(UUID userId, Role role) {
        SessionPrincipal principal = new SessionPrincipal(userId, UUID.randomUUID(), role, "actor@test.local");
        when(currentActor.requirePrincipal()).thenReturn(principal);
        when(currentActor.requireUserId()).thenReturn(userId);
    }

    private Quiz quiz(QuizKind kind) {
        Quiz quiz = new Quiz();
        quiz.setId(UUID.randomUUID());
        quiz.setKind(kind);
        when(editor.requireQuiz(kind)).thenReturn(quiz);
        when(sessions.findByUserIdAndQuizIdOrderByStartedAtAscIdAsc(user.getId(), quiz.getId())).thenReturn(stored);
        return quiz;
    }

    private void session(Quiz quiz, QuizSessionStatus status, boolean test) {
        QuizSession session = new QuizSession();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setQuizId(quiz.getId());
        session.setStatus(status);
        session.setTest(test);
        session.setEndedAt(NOW.minus(Duration.ofMinutes(5)));
        stored.add(session);
    }

    private static void expectCode(Runnable call, ApiErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    void aPracticePassConfirmsNothingAndKeepsTheRole() {
        Quiz foodsaver = quiz(QuizKind.FOODSAVER);
        session(foodsaver, QuizSessionStatus.FAILED, false);
        session(foodsaver, QuizSessionStatus.PASSED, true);

        expectCode(() -> service.confirm(QuizKind.FOODSAVER), ApiErrorCode.QUIZ_NOT_PASSED);
        var status = service.status(foodsaver, user, false);
        assertThat(status.state().passValid()).isFalse();
        assertThat(status.state().triesSinceLastPass()).isEqualTo(1);
        verify(users, never()).changeRole(any(), any());
    }

    @Test
    void aRealPassConfirmedRaisesTheRoleAndEndsOtherSessions() {
        Quiz foodsaver = quiz(QuizKind.FOODSAVER);
        session(foodsaver, QuizSessionStatus.PASSED, false);

        service.confirm(QuizKind.FOODSAVER);
        verify(users).changeRole(user.getId(), Role.FOODSAVER);
        verify(authSessions).revokeOthers();
    }

    @Test
    void theStoreManagerConfirmationNeedsTheConfidentialityNotice() {
        user.setRole(Role.FOODSAVER);
        Quiz storeManager = quiz(QuizKind.STORE_MANAGER);
        session(storeManager, QuizSessionStatus.PASSED, false);

        when(consentChecker.hasCurrentConsent(user.getId(), "CONFIDENTIALITY_NOTICE")).thenReturn(false);
        assertThatThrownBy(() -> service.confirm(QuizKind.STORE_MANAGER)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ApiErrorCode.CONSENT_REQUIRED);
            assertThat(e.getArgs()).containsEntry("kind", "CONFIDENTIALITY_NOTICE");
        });
        verify(users, never()).changeRole(any(), any());

        // without the legal module no consent exists
        when(consents.getIfAvailable()).thenReturn(null);
        expectCode(() -> service.confirm(QuizKind.STORE_MANAGER), ApiErrorCode.CONSENT_REQUIRED);

        when(consents.getIfAvailable()).thenReturn(consentChecker);
        when(consentChecker.hasCurrentConsent(user.getId(), "CONFIDENTIALITY_NOTICE")).thenReturn(true);
        service.confirm(QuizKind.STORE_MANAGER);
        verify(users).changeRole(user.getId(), Role.STORE_MANAGER);
    }

    @Test
    void theFoodsaverConfirmationAsksNoConfidentialityNotice() {
        Quiz foodsaver = quiz(QuizKind.FOODSAVER);
        session(foodsaver, QuizSessionStatus.PASSED, false);
        service.confirm(QuizKind.FOODSAVER);
        verify(consentChecker, never()).hasCurrentConsent(any(), anyString());
    }

    @Test
    void quizSessionsAreReadByTheUserEditorsAndAdministratorsOnly() {
        UUID lea = user.getId();
        UUID other = UUID.randomUUID();

        // herself
        assertThat(service.sessionsOfUser(lea, null)).isEmpty();

        // a stranger
        login(other, Role.FOODSAVER);
        expectCode(() -> service.sessionsOfUser(lea, null), ApiErrorCode.ACCESS_DENIED);

        // the ambassador who administrates her
        when(permissions.mayAdministrateUser(any(), eq(lea))).thenReturn(true);
        assertThat(service.sessionsOfUser(lea, null)).isEmpty();

        // an editor of any quiz
        when(permissions.mayAdministrateUser(any(), eq(lea))).thenReturn(false);
        when(editor.mayEdit(any(), eq(QuizKind.HYGIENE))).thenReturn(true);
        assertThat(service.sessionsOfUser(lea, null)).isEmpty();
    }
}
