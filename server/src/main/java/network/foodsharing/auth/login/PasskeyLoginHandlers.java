package network.foodsharing.auth.login;

import network.foodsharing.common.metrics.OperationsMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.service.LoginService;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Result handlers of {@code POST /login/webauthn}: success creates the {@code fs_session} cookie (LOGIN
 * {method: PASSKEY}) and answers {@code 200 {"authenticated": true, "redirectUrl": "/"}} (same shape as Spring's
 * default); failures answer an {@code ApiError} (401 AUTHENTICATION_REQUIRED, 403 IP_BLOCKED / ACCOUNT_DISABLED).
 */
@Component
public class PasskeyLoginHandlers {

    private static final Logger log = LoggerFactory.getLogger(PasskeyLoginHandlers.class);

    private final LoginService loginService;
    private final ApiErrorResponseWriter errorWriter;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public PasskeyLoginHandlers(LoginService loginService, ApiErrorResponseWriter errorWriter,
                                ObjectMapper objectMapper, AppProperties properties) {
        this.loginService = loginService;
        this.errorWriter = errorWriter;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public AuthenticationSuccessHandler successHandler() {
        return this::onSuccess;
    }

    public AuthenticationFailureHandler failureHandler() {
        return this::onFailure;
    }

    void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            errorWriter.write(response, 401, ApiErrorCode.AUTHENTICATION_REQUIRED, "Unknown passkey user");
            return;
        }
        try {
            loginService.login(userId, LoginMethod.PASSKEY, request, response);
        } catch (ApiException ex) {
            errorWriter.write(response, ex.getStatus().value(), ex.getCode(), ex.getMessage());
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("redirectUrl", properties.getAuth().getDefaultReturnPath());
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        log.info("Passkey login failed: {}", exception.getMessage());
        OperationsMetrics.loginFailed("passkey");
        errorWriter.write(response, 401, ApiErrorCode.AUTHENTICATION_REQUIRED, "Passkey authentication failed");
    }
}
