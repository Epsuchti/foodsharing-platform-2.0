package network.foodsharing.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.AuthAction;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The last step of every login method (Google, magic link, passkey): IP block and account checks, then the
 * {@code fs_session} cookie via {@link AuthSessionService#create} (which records LOGIN {method}).
 */
@Service
public class LoginService {

    private final AuthSessionService sessions;
    private final UserService userService;
    private final IpBlockService ipBlocks;
    private final HistoryService history;

    public LoginService(AuthSessionService sessions, UserService userService, IpBlockService ipBlocks,
                        HistoryService history) {
        this.sessions = sessions;
        this.userService = userService;
        this.ipBlocks = ipBlocks;
        this.history = history;
    }

    /**
     * @throws ApiException 403 IP_BLOCKED, 403 ACCOUNT_DISABLED (both also recorded as LOGIN_FAILED)
     */
    @Transactional
    public CreatedSession login(UUID userId, LoginMethod method, HttpServletRequest request,
                                HttpServletResponse response) {
        if (ipBlocks.isBlocked(HttpExchange.clientIp(request))) {
            fail(userId, method, ApiErrorCode.IP_BLOCKED);
            throw ApiException.forbidden(ApiErrorCode.IP_BLOCKED);
        }
        User user = userService.findActive(userId).orElse(null);
        if (user == null) {
            fail(userId, method, ApiErrorCode.ACCOUNT_DISABLED);
            throw ApiException.forbidden(ApiErrorCode.ACCOUNT_DISABLED);
        }
        return sessions.create(user, method, request, response);
    }

    private void fail(UUID userId, LoginMethod method, ApiErrorCode reason) {
        history.recordInNewTransaction(history.event(AuthAction.LOGIN_FAILED, userId)
                .metadata(Map.of("method", method.name(), "reason", reason.getValue()))
                .build());
    }
}
