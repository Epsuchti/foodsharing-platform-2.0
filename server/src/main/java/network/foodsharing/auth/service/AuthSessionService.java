package network.foodsharing.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.AuthSession;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.repository.AuthSessionRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionAuthenticationToken;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side sessions behind the {@code fs_session} cookie (docs/conventions.md A9): creation (every login method
 * ends here), lookup with sliding expiry, listing and revocation.
 */
@Service
public class AuthSessionService implements SessionAuthenticator {

    private final AuthSessionRepository repository;
    private final UserService userService;
    private final HistoryService history;
    private final CurrentActorService currentActorService;
    private final SessionCookieWriter cookieWriter;
    private final IpHasher ipHasher;
    private final AppProperties properties;
    private final Clock clock;

    public AuthSessionService(AuthSessionRepository repository, UserService userService, HistoryService history,
                              CurrentActorService currentActorService, SessionCookieWriter cookieWriter,
                              IpHasher ipHasher, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.userService = userService;
        this.history = history;
        this.currentActorService = currentActorService;
        this.cookieWriter = cookieWriter;
        this.ipHasher = ipHasher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Creates a session for a successful login: stores the token hash, writes the cookie, sets last_login_at,
     * authenticates the current request and records {@code AuthAction.LOGIN {method, sessionId}}.
     *
     * @throws ApiException 403 ACCOUNT_DISABLED
     */
    @Transactional
    public CreatedSession create(User user, LoginMethod method, HttpServletRequest request, HttpServletResponse response) {
        if (!user.isActive()) {
            throw ApiException.forbidden(ApiErrorCode.ACCOUNT_DISABLED);
        }
        Instant now = clock.instant();
        String rawToken = TokenHasher.newToken();
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(TokenHasher.sha256Hex(rawToken));
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(validity()));
        session.setUserAgent(StringUtils.abbreviate(request.getHeader(HttpHeaders.USER_AGENT), 500));
        session.setIpHash(ipHasher.hash(request.getRemoteAddr()));
        AuthSession saved = repository.save(session);
        userService.markLoggedIn(user.getId());

        SessionPrincipal principal = new SessionPrincipal(user.getId(), saved.getId(), user.getRole(), user.getEmail());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SessionAuthenticationToken.of(principal));
        SecurityContextHolder.setContext(context);

        history.record(history.event(AuthAction.LOGIN, user.getId())
                .metadata(Map.of("method", method.name(), "sessionId", saved.getId().toString()))
                .build());
        cookieWriter.write(response, rawToken);
        return new CreatedSession(saved, principal);
    }

    /** Resolves a cookie token; slides the expiry at most once per touch interval. */
    @Override
    @Transactional
    @NoHistory(reason = "session heartbeat (last_seen_at / sliding expiry), not a user action")
    public Optional<SessionPrincipal> authenticate(String rawToken) {
        Instant now = clock.instant();
        return repository.findByTokenHashWithUser(TokenHasher.sha256Hex(rawToken))
                .filter(session -> session.isActiveAt(now))
                .filter(session -> session.getUser().isActive())
                .map(session -> {
                    touchIfDue(session, now);
                    User user = session.getUser();
                    return new SessionPrincipal(user.getId(), session.getId(), user.getRole(), user.getEmail());
                });
    }

    /** Marks a session as seen now and slides its expiry (bulk update, no optimistic lock conflict). */
    @Transactional
    @NoHistory(reason = "session heartbeat, not a user action")
    public void touch(UUID sessionId) {
        Instant now = clock.instant();
        repository.touch(sessionId, now, now.plus(validity()));
    }

    /** Active sessions of a user, most recently used first (the user themself or ORGA). */
    @Transactional(readOnly = true)
    public List<AuthSession> list(UUID userId) {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        if (!actor.userId().equals(userId) && !currentActorService.isOrga()) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        return repository.findActiveByUserId(userId, clock.instant());
    }

    /**
     * Revokes one of the current user's sessions and records SESSION_REVOKED.
     *
     * @return true when it was the session of the current request
     * @throws ApiException 404 SESSION_NOT_FOUND
     */
    @Transactional
    public boolean revoke(UUID sessionId, SessionRevokeReason reason) {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        Instant now = clock.instant();
        AuthSession session = repository.findByIdAndUserId(sessionId, actor.userId())
                .filter(candidate -> candidate.isActiveAt(now))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SESSION_NOT_FOUND));
        revokeEntity(session, reason, now);
        history.record(history.event(AuthAction.SESSION_REVOKED, actor.userId())
                .metadata(Map.of("sessionId", sessionId.toString(), "reason", reason.name()))
                .build());
        return sessionId.equals(actor.sessionId());
    }

    /** Revokes all other sessions of the current user; records SESSIONS_REVOKED_ALL {count}. */
    @Transactional
    public int revokeOthers() {
        SessionPrincipal actor = currentActorService.requirePrincipal();
        Instant now = clock.instant();
        List<AuthSession> others = repository.findActiveByUserId(actor.userId(), now).stream()
                .filter(session -> !session.getId().equals(actor.sessionId()))
                .toList();
        others.forEach(session -> revokeEntity(session, SessionRevokeReason.LOGOUT_EVERYWHERE, now));
        history.record(history.event(AuthAction.SESSIONS_REVOKED_ALL, actor.userId())
                .metadata("count", others.size())
                .build());
        return others.size();
    }

    /** Ends the current session (if any) and clears the cookie; records LOGOUT when there was a session. */
    @Transactional
    public void logout(HttpServletResponse response) {
        currentActorService.principal().ifPresent(actor -> {
            repository.findById(actor.sessionId()).ifPresent(session ->
                    revokeEntity(session, SessionRevokeReason.LOGOUT, clock.instant()));
            history.record(history.event(AuthAction.LOGOUT, actor.userId())
                    .metadata("sessionId", actor.sessionId().toString())
                    .build());
        });
        cookieWriter.clear(response);
        SecurityContextHolder.clearContext();
    }

    private void touchIfDue(AuthSession session, Instant now) {
        Duration interval = Duration.ofMinutes(properties.getAuth().getSessionTouchIntervalMinutes());
        if (!session.getLastSeenAt().plus(interval).isAfter(now)) {
            Instant expiresAt = now.plus(validity());
            // Bulk update only: changing the managed entity would bump its version and let parallel requests of
            // the same session fail with an optimistic lock conflict.
            repository.touch(session.getId(), now, expiresAt);
        }
    }

    /**
     * Revokes every active session of {@code userId} except {@code keepSessionId} (may be null) inside the caller's
     * transaction; the caller records the history entry of the triggering change (email change, account deletion).
     */
    public int revokeAllExcept(UUID userId, UUID keepSessionId, SessionRevokeReason reason) {
        Instant now = clock.instant();
        List<AuthSession> sessions = repository.findActiveByUserId(userId, now).stream()
                .filter(session -> !session.getId().equals(keepSessionId))
                .toList();
        sessions.forEach(session -> revokeEntity(session, reason, now));
        return sessions.size();
    }

    private static void revokeEntity(AuthSession session, SessionRevokeReason reason, Instant now) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            session.setRevokedReason(reason);
        }
    }

    private Duration validity() {
        return Duration.ofDays(properties.getAuth().getSessionValidityDays());
    }
}
