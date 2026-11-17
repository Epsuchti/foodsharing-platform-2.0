package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestUsers;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserNoteKind;
import network.foodsharing.user.service.UserNoteService;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserVerifiedEvent;
import network.foodsharing.verification.domain.VerificationAction;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Ve2 verification rules the old e2e spec asserted (testing rework, decision 180): a verify/unverify message of at least
 * ten characters, verifying only after the identity is confirmed (C1, no ORGA exception), verifying removes the
 * ambassadors' "new member" bell and announces the verification; nobody confirms their own identity; only
 * administrators of the user act (who that is: PermissionServiceMatrixTest, mayAdministrateUser).
 */
class VerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final String MESSAGE = "Willkommen im Team, Ausweis geprüft!";

    private final UserService users = mock(UserService.class);
    private final UserNoteService notes = mock(UserNoteService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CurrentActorService currentActor = mock(CurrentActorService.class);
    private final PermissionService permissions = mock(PermissionService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final HistoryEvent.Builder event = mock(HistoryEvent.Builder.class, RETURNS_SELF);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final VerificationService service = new VerificationService(users, notes, notifications, currentActor,
            permissions, history, events, List.of(), Clock.fixed(NOW, ZoneOffset.UTC));

    private final User ambassador = saved(TestUsers.user("bot@local", Role.AMBASSADOR));
    private final SessionPrincipal actor = TestUsers.principal(ambassador);
    private final User newcomer = saved(TestUsers.user("pending@local", Role.FOODSHARER));

    VerificationServiceTest() {
        when(currentActor.requirePrincipal()).thenReturn(actor);
        when(currentActor.requireUserId()).thenReturn(ambassador.getId());
        when(users.require(newcomer.getId())).thenReturn(newcomer);
        when(users.require(ambassador.getId())).thenReturn(ambassador);
        when(users.setVerified(eq(newcomer.getId()), anyBoolean(), any())).thenReturn(newcomer);
        when(history.event(any(), any())).thenReturn(event);
    }

    private static User saved(User user) {
        user.setId(UUID.randomUUID());
        return user;
    }

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
        } catch (ApiException e) {
            return e.getCode();
        }
        throw new AssertionError("expected an ApiException");
    }

    @Test
    void verifyingNeedsAMessageOfAtLeastTenCharacters() {
        newcomer.setIdentityConfirmedAt(NOW);
        assertThat(code(() -> service.verify(newcomer.getId(), "kurz"))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.verify(newcomer.getId(), "  neun Zei  "))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThat(code(() -> service.verify(newcomer.getId(), null))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        verify(users, never()).setVerified(any(), anyBoolean(), any());

        service.verify(newcomer.getId(), "zehn Zeich");
        verify(users).setVerified(newcomer.getId(), true, NOW);
    }

    @Test
    void verifyingBeforeTheIdentityIsConfirmedIsAConflictAlsoForOrga() {
        assertThat(code(() -> service.verify(newcomer.getId(), MESSAGE))).isEqualTo(ApiErrorCode.IDENTITY_NOT_CONFIRMED);
        when(currentActor.requirePrincipal()).thenReturn(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(),
                Role.ORGA, "orga@local"));
        assertThat(code(() -> service.verify(newcomer.getId(), MESSAGE))).isEqualTo(ApiErrorCode.IDENTITY_NOT_CONFIRMED);
        verify(users, never()).setVerified(any(), anyBoolean(), any());
    }

    @Test
    void verifyingStoresTheMessageRemovesTheNewMemberBellAndAnnouncesIt() {
        newcomer.setIdentityConfirmedAt(NOW);
        service.verify(newcomer.getId(), "  " + MESSAGE + " ");

        verify(notes).add(newcomer.getId(), UserNoteKind.VERIFICATION_MESSAGE, MESSAGE);
        verify(history).event(VerificationAction.VERIFIED, newcomer.getId());
        verify(event).reason(MESSAGE);
        verify(notifications).removeBells(BellService.identifier(NotificationType.REGION_NEW_MEMBER, newcomer.getId()));
        verify(events).publishEvent(new UserVerifiedEvent(newcomer.getId(), ambassador.getId()));
    }

    @Test
    void verifiedUsersAreNotVerifiedTwiceAndUnverifyingNeedsAMessageToo() {
        newcomer.setIdentityConfirmedAt(NOW);
        newcomer.setVerified(true);
        assertThat(code(() -> service.verify(newcomer.getId(), MESSAGE))).isEqualTo(ApiErrorCode.ALREADY_VERIFIED);
        assertThat(code(() -> service.unverify(newcomer.getId(), "kurz"))).isEqualTo(ApiErrorCode.VALIDATION_FAILED);

        service.unverify(newcomer.getId(), "Hat den Ausweis nicht gezeigt.");
        verify(users).setVerified(newcomer.getId(), false, null);
        verify(history).event(VerificationAction.UNVERIFIED, newcomer.getId());
    }

    @Test
    void unverifyingSomeoneWhoIsNotVerifiedIsAConflict() {
        newcomer.setVerified(false);
        assertThat(code(() -> service.unverify(newcomer.getId(), "Ist gar nicht verifiziert")))
                .isEqualTo(ApiErrorCode.NOT_VERIFIED);
        verify(users, never()).setVerified(any(), anyBoolean(), any());
    }

    @Test
    void onlyAdministratorsOfTheUserVerify() {
        newcomer.setIdentityConfirmedAt(NOW);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions)
                .requireMayAdministrateUser(actor, newcomer.getId());
        assertThat(code(() -> service.verify(newcomer.getId(), MESSAGE))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(code(() -> service.confirmIdentity(newcomer.getId()))).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        verify(users, never()).setVerified(any(), anyBoolean(), any());
        verify(users, never()).setIdentityConfirmation(any(), any(), any());
    }

    @Test
    void nobodyConfirmsTheirOwnIdentityAndAConfirmedOneOnlyOnce() {
        assertThat(code(() -> service.confirmIdentity(ambassador.getId())))
                .isEqualTo(ApiErrorCode.CANNOT_CONFIRM_OWN_IDENTITY);
        verifyNoInteractions(permissions);

        service.confirmIdentity(newcomer.getId());
        verify(users).setIdentityConfirmation(newcomer.getId(), NOW, ambassador.getId());
        verify(history).event(VerificationAction.IDENTITY_CONFIRMED, newcomer.getId());

        newcomer.setIdentityConfirmedAt(NOW);
        assertThat(code(() -> service.confirmIdentity(newcomer.getId())))
                .isEqualTo(ApiErrorCode.IDENTITY_ALREADY_CONFIRMED);
    }

    @Test
    void onlyOrgaRevertsAnIdentityConfirmation() {
        newcomer.setIdentityConfirmedAt(NOW);
        doThrow(ApiException.forbidden(ApiErrorCode.ACCESS_DENIED)).when(permissions).requireOrga(actor);
        assertThatThrownBy(() -> service.revertIdentity(newcomer.getId(), "Neu prüfen")).isInstanceOf(ApiException.class);
        verify(users, never()).setIdentityConfirmation(any(), any(), any());
    }
}
