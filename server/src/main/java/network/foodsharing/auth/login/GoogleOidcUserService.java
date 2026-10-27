package network.foodsharing.auth.login;

import java.util.Locale;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.auth.domain.IdentityProvider;
import network.foodsharing.auth.service.AccountService;
import network.foodsharing.auth.service.VerifiedIdentity;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.HttpExchange;
import network.foodsharing.user.domain.User;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Google sign-in (plan §8.1): requires {@code email_verified}, then resolves the account by Google subject or
 * verified email (creating it with name, avatar and language prefilled). Failures become an
 * {@link OAuth2AuthenticationException} whose error code is the {@code ApiErrorCode} the failure handler shows.
 */
@Component
public class GoogleOidcUserService extends OidcUserService {

    private final AccountService accountService;

    public GoogleOidcUserService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String email = oidcUser.getEmail();
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw failure(ApiErrorCode.GOOGLE_EMAIL_NOT_VERIFIED, "Google email missing or not verified", null);
        }
        VerifiedIdentity identity = new VerifiedIdentity(IdentityProvider.GOOGLE, oidcUser.getSubject(), email, email,
                oidcUser.getFullName(), oidcUser.getGivenName(), oidcUser.getFamilyName(), oidcUser.getPicture(),
                language(oidcUser.getLocale()));
        try {
            User user = accountService.findOrCreateByVerifiedEmail(identity,
                    HttpExchange.clientIp(HttpExchange.request()));
            return new FoodsharingOidcUser(user.getId(), oidcUser);
        } catch (ApiException ex) {
            throw failure(ex.getCode(), ex.getMessage(), ex);
        }
    }

    /** Google locale ({@code de}, {@code de-CH}, {@code fr-CH}, {@code gsw} …) → platform language, else null. */
    static Language language(String locale) {
        if (locale == null || locale.isBlank()) {
            return null;
        }
        String tag = locale.replace('_', '-').toLowerCase(Locale.ROOT);
        String primary = tag.split("-")[0];
        return switch (primary) {
            case "gsw" -> Language.GWS;
            case "de" -> Language.DE;
            case "en" -> Language.EN;
            case "fr" -> Language.FR;
            case "it" -> Language.IT;
            default -> null;
        };
    }

    private static OAuth2AuthenticationException failure(ApiErrorCode code, String description, Throwable cause) {
        OAuth2Error error = new OAuth2Error(code.getValue(), description, null);
        return cause == null ? new OAuth2AuthenticationException(error)
                : new OAuth2AuthenticationException(error, cause);
    }
}
