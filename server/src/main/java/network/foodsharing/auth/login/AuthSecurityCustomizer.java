package network.foodsharing.auth.login;

import network.foodsharing.auth.service.PasskeyService;
import network.foodsharing.auth.service.ReturnUrlValidator;
import network.foodsharing.config.AppProperties;
import network.foodsharing.security.HttpSecurityCustomizer;
import network.foodsharing.security.PermissionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.stereotype.Component;

/**
 * Adds the login mechanisms of the auth module to the main chain ({@code config.SecurityConfig}):
 * <ul>
 *     <li>Google: {@code GET /oauth2/authorization/google?returnUrl=…} → Google → {@code /login/oauth2/code/google}
 *     → {@link GoogleLoginSuccessHandler} (cookie + redirect) / {@link GoogleLoginFailureHandler}
 *     ({@code /login?error=CODE})</li>
 *     <li>Passkeys (Spring Security WebAuthn): {@code POST /webauthn/register/options}, {@code POST /webauthn/register}
 *     (both need a session), {@code POST /webauthn/authenticate/options}, {@code POST /login/webauthn} →
 *     {@link PasskeyLoginHandlers}</li>
 * </ul>
 * Neither stores the security context in the servlet session; the login result is the {@code fs_session} cookie.
 */
@Component
public class AuthSecurityCustomizer implements HttpSecurityCustomizer {

    private final AppProperties properties;
    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final ReturnUrlValidator returnUrlValidator;
    private final ReturnUrlCookie returnUrlCookie;
    private final GoogleOidcUserService oidcUserService;
    private final GoogleLoginSuccessHandler googleSuccess;
    private final GoogleLoginFailureHandler googleFailure;
    private final PasskeyLoginHandlers passkeyHandlers;
    private final PasskeyService passkeys;

    public AuthSecurityCustomizer(AppProperties properties, ObjectProvider<ClientRegistrationRepository> registrations,
                                  ReturnUrlValidator returnUrlValidator, ReturnUrlCookie returnUrlCookie,
                                  GoogleOidcUserService oidcUserService, GoogleLoginSuccessHandler googleSuccess,
                                  GoogleLoginFailureHandler googleFailure, PasskeyLoginHandlers passkeyHandlers,
                                  PasskeyService passkeys) {
        this.properties = properties;
        this.registrations = registrations;
        this.returnUrlValidator = returnUrlValidator;
        this.returnUrlCookie = returnUrlCookie;
        this.oidcUserService = oidcUserService;
        this.googleSuccess = googleSuccess;
        this.googleFailure = googleFailure;
        this.passkeyHandlers = passkeyHandlers;
        this.passkeys = passkeys;
    }

    @Override
    public void customize(HttpSecurity http) throws Exception {
        ClientRegistrationRepository clientRegistrations = registrations.getIfAvailable();
        if (properties.getAuth().getGoogle().isEnabled() && clientRegistrations != null) {
            http.oauth2Login(oauth -> oauth
                    .loginPage(GoogleLoginFailureHandler.LOGIN_PAGE)
                    .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
                            new GoogleAuthorizationRequestResolver(clientRegistrations, returnUrlValidator,
                                    returnUrlCookie)))
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    .successHandler(googleSuccess)
                    .failureHandler(googleFailure));
        }
        if (properties.getAuth().getPasskeys().isEnabled()) {
            http.setSharedObject(UserDetailsService.class, passkeyUserDetailsService());
            AppProperties.Auth.Passkeys config = properties.getAuth().getPasskeys();
            http.webAuthn(webAuthn -> webAuthn
                    .rpId(config.getRpId())
                    .rpName(config.getRpName())
                    .disableDefaultRegistrationPage(true)
                    .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
                        @Override
                        public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                            filter.setAuthenticationSuccessHandler(passkeyHandlers.successHandler());
                            filter.setAuthenticationFailureHandler(passkeyHandlers.failureHandler());
                            filter.setSecurityContextRepository(new RequestAttributeSecurityContextRepository());
                            return filter;
                        }
                    }));
        }
    }

    /** Loads the passkey owner by user id (Spring Security's WebAuthn "username"); disabled accounts are unknown. */
    UserDetailsService passkeyUserDetailsService() {
        return username -> JpaPublicKeyCredentialUserEntityRepository.parseUserId(username)
                .flatMap(passkeys::findActiveUser)
                .map(user -> User.withUsername(username)
                        .password("")
                        .authorities(PermissionService.authoritiesFor(user.getRole()))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown or disabled user"));
    }
}
