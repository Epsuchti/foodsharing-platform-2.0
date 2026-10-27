package network.foodsharing.auth.web;

import jakarta.servlet.http.HttpServletRequest;
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
import network.foodsharing.auth.service.AuthMethodsService;
import network.foodsharing.auth.service.AuthSessionService;
import network.foodsharing.auth.service.MagicLinkLogin;
import network.foodsharing.auth.service.MagicLinkService;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionCookieWriter;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessions, magic links, login methods and the security log. Google login and passkey ceremonies are framework
 * endpoints ({@code auth.login.AuthSecurityCustomizer}), not part of this controller.
 */
@RestController
public class AuthController implements AuthApi {

    /** {@code MessageResponse.message} of {@code requestMagicLink}, the same whether or not the account exists. */
    static final String MAGIC_LINK_SENT = "MAGIC_LINK_SENT";

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
    private final MagicLinkService magicLinkService;
    private final AuthMethodsService authMethodsService;

    public AuthController(AuthSessionService sessionService, UserService userService,
                          CurrentActorService currentActorService, PermissionService permissionService,
                          HistoryQueryService historyQueryService, HistoryMapper historyMapper, AuthMapper mapper,
                          SessionCookieWriter cookieWriter, MagicLinkService magicLinkService,
                          AuthMethodsService authMethodsService) {
        this.sessionService = sessionService;
        this.userService = userService;
        this.currentActorService = currentActorService;
        this.permissionService = permissionService;
        this.historyQueryService = historyQueryService;
        this.historyMapper = historyMapper;
        this.mapper = mapper;
        this.cookieWriter = cookieWriter;
        this.magicLinkService = magicLinkService;
        this.authMethodsService = authMethodsService;
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
        HttpServletRequest request = HttpExchange.request();
        magicLinkService.request(magicLinkRequest.getEmail(), magicLinkRequest.getReturnUrl(),
                magicLinkRequest.getLanguage(), HttpExchange.clientIp(request));
        MessageResponse response = new MessageResponse();
        response.setMessage(MAGIC_LINK_SENT);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Override
    public ResponseEntity<SessionInfo> verifyMagicLink(MagicLinkVerifyRequest magicLinkVerifyRequest) {
        MagicLinkLogin login = magicLinkService.verify(magicLinkVerifyRequest.getToken(), HttpExchange.request(),
                HttpExchange.response());
        SessionPrincipal principal = login.session().principal();
        return ResponseEntity.ok(mapper.sessionInfo(principal, login.user(),
                permissionService.effectivePermissions(principal)));
    }

    @Override
    public ResponseEntity<AuthMethods> listAuthMethods() {
        AuthMethodsService.LoginMethods methods = authMethodsService.list();
        return ResponseEntity.ok(mapper.toMethods(methods.identities(), methods.passkeys()));
    }

    @Override
    public ResponseEntity<Void> unlinkAuthMethod(UUID identityId) {
        authMethodsService.unlinkIdentity(identityId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<PasskeyView> renamePasskey(UUID passkeyId, PasskeyRenameRequest passkeyRenameRequest) {
        return ResponseEntity.ok(mapper.toView(authMethodsService.renamePasskey(passkeyId,
                passkeyRenameRequest.getLabel())));
    }

    @Override
    public ResponseEntity<Void> deletePasskey(UUID passkeyId) {
        authMethodsService.deletePasskey(passkeyId);
        return ResponseEntity.noContent().build();
    }
}
