package network.foodsharing.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.EmailChange;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.repository.AuthIdentityRepository;
import network.foodsharing.auth.repository.EmailChangeRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.domain.UserAction;
import network.foodsharing.user.service.EmailChangePort;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email change (A5, upstream {@code fs_mailchange} / {@code ChangeHistoryKey}): one pending change per user (a new
 * request replaces it); the new address gets {@code user/email-change-confirm} (link
 * {@code /account/email/confirm?token=}, 24 h), the old one {@code user/email-change-notice} (link
 * {@code /account/email/abort?token=}). Confirming (logged-in self) sets the address, updates the EMAIL login
 * identity and revokes all other sessions. History entries carry redacted addresses only.
 */
@Service
public class EmailChangeService implements EmailChangePort {

    static final Duration VALIDITY = Duration.ofHours(24);
    static final String CONFIRM_TEMPLATE = "user/email-change-confirm";
    static final String NOTICE_TEMPLATE = "user/email-change-notice";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[a-z0-9-]+(\\.[a-z0-9-]+)*$");

    private final EmailChangeRepository changes;
    private final AuthIdentityRepository identities;
    private final EmailBlocklistService blocklist;
    private final AuthSessionService sessions;
    private final UserService users;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final MailOutboxService outbox;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public EmailChangeService(EmailChangeRepository changes, AuthIdentityRepository identities,
                              EmailBlocklistService blocklist, AuthSessionService sessions, UserService users,
                              CurrentActorService actors, PermissionService permissions, MailOutboxService outbox,
                              HistoryService history, AppProperties properties, Clock clock) {
        this.changes = changes;
        this.identities = identities;
        this.blocklist = blocklist;
        this.sessions = sessions;
        this.users = users;
        this.actors = actors;
        this.permissions = permissions;
        this.outbox = outbox;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /** {@code anna@example.org} → {@code a***@example.org} (history never stores full addresses). */
    public static String redact(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pending> pending() {
        UUID me = actors.requireUserId();
        Instant now = clock.instant();
        return changes.findByUserIdOrderByCreatedAtDescIdDesc(me).stream().filter(c -> c.isPendingAt(now)).findFirst()
                .map(c -> new Pending(c.getNewEmail(), c.getCreatedAt()));
    }

    /**
     * @throws ApiException 403 ACCESS_DENIED (others without mayAdministrateUser), 404 USER_NOT_FOUND,
     *                      400 EMAIL_INVALID / EMAIL_BLOCKED, 409 EMAIL_TAKEN
     */
    @Override
    @Transactional
    public void request(UUID userId, String rawEmail) {
        SessionPrincipal actor = actors.requirePrincipal();
        User user = users.requireActive(userId);
        if (!actor.userId().equals(userId)) {
            permissions.requireMayAdministrateUser(actor, userId);
        }
        String email = UserService.normalizeEmail(rawEmail == null ? "" : rawEmail);
        if (!EMAIL.matcher(email).matches() || email.length() > 320) {
            throw ApiException.badRequest(ApiErrorCode.EMAIL_INVALID);
        }
        if (blocklist.isBlocked(email)) {
            throw ApiException.badRequest(ApiErrorCode.EMAIL_BLOCKED);
        }
        if (email.equalsIgnoreCase(user.getEmail()) || isTaken(email, userId)) {
            throw ApiException.conflict(ApiErrorCode.EMAIL_TAKEN);
        }
        Instant now = clock.instant();
        changes.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream().filter(c -> c.isPendingAt(now))
                .forEach(c -> c.setAbortedAt(now));
        String token = TokenHasher.newToken();
        String abortToken = TokenHasher.newToken();
        EmailChange change = new EmailChange();
        change.setUserId(userId);
        change.setOldEmail(user.getEmail());
        change.setNewEmail(email);
        change.setTokenHash(TokenHasher.sha256Hex(token));
        change.setAbortTokenHash(TokenHasher.sha256Hex(abortToken));
        change.setExpiresAt(now.plus(VALIDITY));
        changes.save(change);
        history.record(history.event(UserAction.EMAIL_CHANGE_REQUESTED, userId)
                .change("email", redact(user.getEmail()), redact(email)).build());

        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", user.getDisplayName());
        variables.put("newEmail", email);
        variables.put("validityHours", VALIDITY.toHours());
        variables.put("link", properties.getBaseUrl() + "/account/email/confirm?token=" + token);
        outbox.enqueue(new MailDraft(email, CONFIRM_TEMPLATE, user.getPreferredLanguage(), variables, null,
                MailDraft.AUTH, null));
        Map<String, Object> notice = new HashMap<>(variables);
        notice.put("link", properties.getBaseUrl() + "/account/email/abort?token=" + abortToken);
        notice.put("newEmail", redact(email));
        outbox.enqueue(new MailDraft(user.getEmail(), NOTICE_TEMPLATE, user.getPreferredLanguage(), notice, userId,
                MailDraft.AUTH, null));
    }

    private boolean isTaken(String email, UUID userId) {
        if (users.findByEmail(email).filter(other -> !other.getId().equals(userId)).isPresent()) {
            return true;
        }
        return identities.findByProviderAndSubject(IdentityProvider.EMAIL, email)
                .filter(identity -> !identity.getUser().getId().equals(userId)).isPresent();
    }

    /** @throws ApiException 400 EMAIL_CHANGE_TOKEN_INVALID (unknown, someone else's, used, aborted or expired) */
    @Override
    @Transactional
    public User confirm(String token) {
        SessionPrincipal actor = actors.requirePrincipal();
        Instant now = clock.instant();
        EmailChange change = changes.findByTokenHash(TokenHasher.sha256Hex(token == null ? "" : token))
                .filter(c -> c.getUserId().equals(actor.userId()) && c.isPendingAt(now))
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID));
        if (isTaken(change.getNewEmail(), actor.userId())) {
            throw ApiException.conflict(ApiErrorCode.EMAIL_TAKEN);
        }
        change.setCompletedAt(now);
        String oldEmail = change.getOldEmail();
        User user = users.changeEmail(actor.userId(), change.getNewEmail(), now);
        for (AuthIdentity identity : identities.findByUserId(actor.userId())) {
            if (identity.getProvider() == IdentityProvider.EMAIL) {
                identity.setSubject(user.getEmail());
                identity.setLabel(user.getEmail());
            }
        }
        int revoked = sessions.revokeAllExcept(actor.userId(), actor.sessionId(), SessionRevokeReason.EMAIL_CHANGED);
        history.record(history.event(UserAction.EMAIL_CHANGE_COMPLETED, actor.userId())
                .change("email", redact(oldEmail), redact(user.getEmail()))
                .metadata("sessionsRevoked", revoked).build());
        return user;
    }

    @Override
    @Transactional
    public void abort() {
        UUID me = actors.requireUserId();
        Instant now = clock.instant();
        changes.findByUserIdOrderByCreatedAtDescIdDesc(me).stream().filter(c -> c.isPendingAt(now))
                .forEach(c -> abortChange(c, now));
    }

    /** @throws ApiException 400 EMAIL_CHANGE_TOKEN_INVALID */
    @Override
    @Transactional
    public void abortByToken(String token) {
        Instant now = clock.instant();
        EmailChange change = changes.findByAbortTokenHash(TokenHasher.sha256Hex(token == null ? "" : token))
                .filter(c -> c.isPendingAt(now))
                .orElseThrow(() -> ApiException.badRequest(ApiErrorCode.EMAIL_CHANGE_TOKEN_INVALID));
        abortChange(change, now);
    }

    private void abortChange(EmailChange change, Instant now) {
        change.setAbortedAt(now);
        history.record(history.event(UserAction.EMAIL_CHANGE_ABORTED, change.getUserId())
                .change("email", redact(change.getOldEmail()), redact(change.getNewEmail())).build());
    }
}
