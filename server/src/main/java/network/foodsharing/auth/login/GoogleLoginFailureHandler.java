package network.foodsharing.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Failed Google login → {@code /login?error=<ApiErrorCode>[&returnUrl=…]}. The code is the one set by
 * {@link GoogleOidcUserService} (GOOGLE_EMAIL_NOT_VERIFIED, EMAIL_DOMAIN_BLOCKED, IP_BLOCKED, ACCOUNT_DISABLED);
 * everything else (user cancelled, invalid token, provider error) is GOOGLE_LOGIN_FAILED.
 */
@Component
public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {

    public static final String LOGIN_PAGE = "/login";

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);
    private static final Set<String> KNOWN_CODES = Arrays.stream(ApiErrorCode.values())
            .map(ApiErrorCode::getValue)
            .collect(Collectors.toUnmodifiableSet());

    private final ReturnUrlCookie returnUrlCookie;

    public GoogleLoginFailureHandler(ReturnUrlCookie returnUrlCookie) {
        this.returnUrlCookie = returnUrlCookie;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String code = ApiErrorCode.GOOGLE_LOGIN_FAILED.getValue();
        if (exception instanceof OAuth2AuthenticationException oauth
                && KNOWN_CODES.contains(oauth.getError().getErrorCode())) {
            code = oauth.getError().getErrorCode();
        }
        log.info("Google login failed: {} ({})", code, exception.getMessage());
        String returnUrl = returnUrlCookie.read(request).orElse(null);
        returnUrlCookie.clear(response);
        GoogleLoginSuccessHandler.invalidateHandshakeSession(request);
        response.sendRedirect(errorUrl(code, returnUrl));
    }

    static String errorUrl(String code, String returnUrl) {
        String url = LOGIN_PAGE + "?error=" + URLEncoder.encode(code, StandardCharsets.UTF_8);
        if (returnUrl != null) {
            url += "&returnUrl=" + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8);
        }
        return url;
    }
}
