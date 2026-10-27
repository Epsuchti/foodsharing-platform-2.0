package network.foodsharing.auth.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AuthApi;
import network.foodsharing.api.model.AuthMethods;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.MagicLinkRequest;
import network.foodsharing.api.model.MagicLinkVerifyRequest;
import network.foodsharing.api.model.MessageResponse;
import network.foodsharing.api.model.PasskeyRenameRequest;
import network.foodsharing.api.model.PasskeyView;
import network.foodsharing.api.model.SessionInfo;
import network.foodsharing.api.model.SessionView;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.SessionRevokeReason;
import network.foodsharing.auth.mapper.AuthMapper;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session endpoints (T1). Login-method endpoints answer 501 NOT_IMPLEMENTED until T4 implements them.
 */
@RestController
public class AuthController implements AuthApi {

    private static final List<String> SECURITY_LOG_ACTIONS = java.util.Arrays.stream(AuthAction.values())
            .filter(action -> action.subjectType() == SubjectType.USER)
            .map(AuthAction::code)
            .toList();

    private final AuthSessionService sessionService;
    private final UserService userService;
    private final CurrentActorService currentActorService;
    private final PermissionService permissionService;
    private final HistoryQueryService historyQueryService;
    private final HistoryMapper historyMapper;
    private final AuthMapper mapper;
    private final SessionCookieWriter cookieWriter;

    public AuthController(AuthSessionService sessionService, UserService userService,
                          CurrentActorService currentActorService, PermissionService permissionService,
                          HistoryQueryService historyQueryService, HistoryMapper historyMapper, AuthMapper mapper,
                          SessionCookieWriter cookieWriter) {
        this.sessionService = sessionService;
        this.userService = userService;
        this.currentActorService = currentActorService;
        this.permissionService = permissionService;
        this.historyQueryService = historyQueryService;
        this.historyMapper = historyMapper;
        this.mapper = mapper;
        this.cookieWriter = cookieWriter;
    }

    @Override
    public ResponseEntity<SessionInfo> getSession() {
        SessionInfo info = currentActorService.principal()
                .flatMap(principal -> userService.findActive(principal.userId())
                        .map(user -> mapper.sessionInfo(principal, user, permissionService.effectivePermissions(principal))))
                .orElseGet(mapper::anonymous);
        return ResponseEntity.ok(info);
    }

    @Override
    public ResponseEntity<Void> logout() {
        sessionService.logout(HttpExchange.response());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<SessionView>> listSessions() {
        SessionPrincipal principal = currentActorService.requirePrincipal();
        return ResponseEntity.ok(sessionService.list(principal.userId()).stream()
                .map(session -> mapper.toView(session, principal))
                .toList());
    }

    @Override
    public ResponseEntity<Void> revokeSession(UUID sessionId) {
        boolean current = sessionService.revoke(sessionId, SessionRevokeReason.REVOKED_BY_USER);
        if (current) {
            cookieWriter.clear(HttpExchange.response());
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> logoutEverywhere() {
        sessionService.revokeOthers();
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getSecurityLog(Integer page, Integer size) {
        UUID userId = currentActorService.requireUserId();
        return ResponseEntity.ok(historyMapper.toPage(historyQueryService.listForSubject(SubjectType.USER, userId,
                SECURITY_LOG_ACTIONS, page == null ? 0 : page, size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<MessageResponse> requestMagicLink(MagicLinkRequest magicLinkRequest) {
        throw ApiException.notImplemented("requestMagicLink");
    }

    @Override
    public ResponseEntity<SessionInfo> verifyMagicLink(MagicLinkVerifyRequest magicLinkVerifyRequest) {
        throw ApiException.notImplemented("verifyMagicLink");
    }

    @Override
    public ResponseEntity<AuthMethods> listAuthMethods() {
        throw ApiException.notImplemented("listAuthMethods");
    }

    @Override
    public ResponseEntity<Void> unlinkAuthMethod(UUID identityId) {
        throw ApiException.notImplemented("unlinkAuthMethod");
    }

    @Override
    public ResponseEntity<PasskeyView> renamePasskey(UUID passkeyId, PasskeyRenameRequest passkeyRenameRequest) {
        throw ApiException.notImplemented("renamePasskey");
    }

    @Override
    public ResponseEntity<Void> deletePasskey(UUID passkeyId) {
        throw ApiException.notImplemented("deletePasskey");
    }
}
