package network.foodsharing.verification.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserNoteKind;
import network.foodsharing.user.service.UserNoteService;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserVerifiedEvent;
import network.foodsharing.verification.domain.VerificationAction;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification and identity confirmation (plan §13.9 Ve2): ambassadors / FSMANAGEMENT admins of a region the user is a
 * member of (and ORGA) verify with a required message (stored as a user note and as the history reason); un-verifying
 * asks the {@link UnverifyGuard}s first. Identity is confirmed by an administrator other than the user; only ORGA
 * reverts it.
 */
@Service
public class VerificationService {

    static final int MIN_MESSAGE_LENGTH = 10;

    public record IdentityView(boolean confirmed, java.time.Instant confirmedAt, User confirmedBy) {
    }

    private final UserService users;
    private final UserNoteService notes;
    private final NotificationService notifications;
    private final CurrentActorService currentActor;
    private final PermissionService permissions;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final List<UnverifyGuard> guards;
    private final Clock clock;

    public VerificationService(UserService users, UserNoteService notes, NotificationService notifications,
                               CurrentActorService currentActor, PermissionService permissions,
                               HistoryService history, ApplicationEventPublisher events, List<UnverifyGuard> guards,
                               Clock clock) {
        this.users = users;
        this.notes = notes;
        this.notifications = notifications;
        this.currentActor = currentActor;
        this.permissions = permissions;
        this.history = history;
        this.events = events;
        this.guards = guards;
        this.clock = clock;
    }

    /** @throws ApiException 404 USER_NOT_FOUND, 403 ACCESS_DENIED, 400 VALIDATION_FAILED, 409 ALREADY_VERIFIED */
    @Transactional
    public User verify(UUID userId, String message) {
        User user = requireAdministrable(userId);
        String text = requireMessage(message);
        if (user.isVerified()) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_VERIFIED);
        }
        users.setVerified(userId, true, clock.instant());
        notes.add(userId, UserNoteKind.VERIFICATION_MESSAGE, text);
        history.record(history.event(VerificationAction.VERIFIED, userId).reason(text).build());
        notifications.removeBells(BellService.identifier(NotificationType.REGION_NEW_MEMBER, userId));
        events.publishEvent(new UserVerifiedEvent(userId, currentActor.requireUserId()));
        return user;
    }

    /** @throws ApiException 404, 403, 400 VALIDATION_FAILED, 409 NOT_VERIFIED / guard codes (HAS_FUTURE_PICKUPS) */
    @Transactional
    public User unverify(UUID userId, String message) {
        User user = requireAdministrable(userId);
        String text = requireMessage(message);
        if (!user.isVerified()) {
            throw ApiException.conflict(ApiErrorCode.NOT_VERIFIED);
        }
        guards.forEach(guard -> guard.check(userId));
        users.setVerified(userId, false, null);
        notes.add(userId, UserNoteKind.VERIFICATION_MESSAGE, text);
        history.record(history.event(VerificationAction.UNVERIFIED, userId).reason(text).build());
        return user;
    }

    /** The user themself or an administrator of the user. @throws ApiException 404, 403 */
    @Transactional(readOnly = true)
    public IdentityView identity(UUID userId) {
        User user = users.require(userId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!actor.userId().equals(userId)) {
            permissions.requireMayAdministrateUser(actor, userId);
        }
        return view(user);
    }

    /** @throws ApiException 404, 403 ACCESS_DENIED / CANNOT_CONFIRM_OWN_IDENTITY, 409 IDENTITY_ALREADY_CONFIRMED */
    @Transactional
    public IdentityView confirmIdentity(UUID userId) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (actor.userId().equals(userId)) {
            throw ApiException.forbidden(ApiErrorCode.CANNOT_CONFIRM_OWN_IDENTITY);
        }
        User user = requireAdministrable(userId);
        if (user.getIdentityConfirmedAt() != null) {
            throw ApiException.conflict(ApiErrorCode.IDENTITY_ALREADY_CONFIRMED);
        }
        users.setIdentityConfirmation(userId, clock.instant(), actor.userId());
        history.record(history.event(VerificationAction.IDENTITY_CONFIRMED, userId).build());
        return view(user);
    }

    /** @throws ApiException 404, 403 (ORGA only), 400 VALIDATION_FAILED, 409 IDENTITY_NOT_CONFIRMED */
    @Transactional
    public IdentityView revertIdentity(UUID userId, String reason) {
        permissions.requireOrga(currentActor.requirePrincipal());
        User user = users.require(userId);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        if (user.getIdentityConfirmedAt() == null) {
            throw ApiException.conflict(ApiErrorCode.IDENTITY_NOT_CONFIRMED);
        }
        users.setIdentityConfirmation(userId, null, null);
        history.record(history.event(VerificationAction.IDENTITY_CONFIRMATION_REVERTED, userId)
                .reason(reason.strip()).build());
        return view(user);
    }

    private IdentityView view(User user) {
        User by = user.getIdentityConfirmedBy() == null ? null
                : users.findAll(List.of(user.getIdentityConfirmedBy())).stream().findFirst().orElse(null);
        return new IdentityView(user.getIdentityConfirmedAt() != null, user.getIdentityConfirmedAt(), by);
    }

    private User requireAdministrable(UUID userId) {
        User user = users.require(userId);
        permissions.requireMayAdministrateUser(currentActor.requirePrincipal(), userId);
        return user;
    }

    private static String requireMessage(String message) {
        if (message == null || message.strip().length() < MIN_MESSAGE_LENGTH) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return message.strip();
    }
}
