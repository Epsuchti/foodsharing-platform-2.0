package network.foodsharing.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import network.foodsharing.auth.domain.LoginMethod;
import network.foodsharing.auth.service.LoginService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * After a successful Google callback: creates the {@code fs_session} cookie (LOGIN {method: GOOGLE}), drops the
 * handshake session and redirects to the return URL from the signed {@code fs_oauth_return} cookie (default
 * {@code app.auth.default-return-path}).
 */
@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);

    private final LoginService loginService;
    private final ReturnUrlCookie returnUrlCookie;
    private final AppProperties properties;

    public GoogleLoginSuccessHandler(LoginService loginService, ReturnUrlCookie returnUrlCookie,
                                     AppProperties properties) {
        this.loginService = loginService;
        this.returnUrlCookie = returnUrlCookie;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = returnUrlCookie.read(request).orElse(properties.getAuth().getDefaultReturnPath());
        returnUrlCookie.clear(response);
        invalidateHandshakeSession(request);
        if (!(authentication.getPrincipal() instanceof FoodsharingOidcUser user)) {
            response.sendRedirect(GoogleLoginFailureHandler.errorUrl("GOOGLE_LOGIN_FAILED", null));
            return;
        }
        try {
            loginService.login(user.userId(), LoginMethod.GOOGLE, request, response);
        } catch (ApiException ex) {
            log.info("Google login of user {} rejected: {}", user.userId(), ex.getCode());
            response.sendRedirect(GoogleLoginFailureHandler.errorUrl(ex.getCode().getValue(), target));
            return;
        }
        response.sendRedirect(target);
    }

    static void invalidateHandshakeSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
