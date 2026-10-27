package network.foodsharing.auth.login;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import network.foodsharing.auth.service.ReturnUrlValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@code GET /oauth2/authorization/google?returnUrl=/some/page}: the default resolver plus the {@code returnUrl}
 * query parameter, which is validated ({@link ReturnUrlValidator}) and kept in the signed {@link ReturnUrlCookie}
 * until the Google callback. Invalid return URLs are ignored (the login then lands on the default page).
 */
public class GoogleAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    public static final String RETURN_URL_PARAMETER = "returnUrl";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final ReturnUrlValidator validator;
    private final ReturnUrlCookie cookie;

    public GoogleAuthorizationRequestResolver(ClientRegistrationRepository registrations, ReturnUrlValidator validator,
                                              ReturnUrlCookie cookie) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, AUTHORIZATION_BASE_URI);
        this.validator = validator;
        this.cookie = cookie;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return remember(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return remember(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest remember(HttpServletRequest request, OAuth2AuthorizationRequest resolved) {
        if (resolved == null) {
            return null;
        }
        HttpServletResponse response = currentResponse();
        if (response != null) {
            Optional<String> returnUrl = validator.sanitize(request.getParameter(RETURN_URL_PARAMETER));
            if (returnUrl.isPresent()) {
                cookie.write(response, returnUrl.get());
            } else {
                cookie.clear(response);
            }
        }
        return resolved;
    }

    private static HttpServletResponse currentResponse() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getResponse();
        }
        return null;
    }
}
