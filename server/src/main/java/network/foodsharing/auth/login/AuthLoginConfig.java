package network.foodsharing.auth.login;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.auth.service.PasskeyService;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.MutableClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

/**
 * Beans picked up by Spring Security's OAuth2 login and WebAuthn configurers:
 * <ul>
 *     <li>the id-token decoder factory: validators on the application {@link Clock} (fixed in e2e) and an explicit
 *     issuer check against {@code app.auth.google.issuer} (no discovery, docs/conventions.md A9)</li>
 *     <li>the WebAuthn relying party ({@code app.auth.passkeys.*}), JPA-backed, showing email and display name in
 *     the authenticator instead of the internal user id</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class AuthLoginConfig {

    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(AppProperties properties, Clock clock) {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        Clock tokenClock = tokenClock(clock);
        factory.setJwtValidatorFactory(registration -> {
            JwtTimestampValidator timestamps = new JwtTimestampValidator();
            timestamps.setClock(tokenClock);
            // Boot applies its built-in "google" template (issuer https://accounts.google.com) to the registration;
            // the issuer is checked by issuerValidator against app.auth.google.issuer instead (fake OIDC in local/e2e).
            OidcIdTokenValidator idToken = new OidcIdTokenValidator(
                    ClientRegistration.withClientRegistration(registration).issuerUri(null).build());
            idToken.setClock(tokenClock);
            return new DelegatingOAuth2TokenValidator<>(timestamps, idToken,
                    issuerValidator(properties.getAuth().getGoogle().getIssuer()));
        });
        return factory;
    }

    /**
     * The clock id-token timestamps (iat/exp) are checked against: the identity provider's real time, not the
     * application clock that local/e2e may fix at another instant (decision 20 — the fake provider signs with real
     * time too, like Google).
     */
    static Clock tokenClock(Clock clock) {
        return clock instanceof MutableClock mutable ? mutable.realClock() : clock;
    }

    /** Accepts {@code expected} and, as Google documents, the same issuer without {@code https://}. */
    static OAuth2TokenValidator<Jwt> issuerValidator(String expected) {
        return jwt -> {
            String issuer = jwt.getClaimAsString("iss");
            if (issuer != null && (issuer.equals(expected) || ("https://" + issuer).equals(expected))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_id_token",
                    "Unexpected id_token issuer " + issuer, null));
        };
    }

    @Bean
    public WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            JpaPublicKeyCredentialUserEntityRepository userEntities, JpaUserCredentialRepository credentials,
            PasskeyService passkeys, AppProperties properties) {
        AppProperties.Auth.Passkeys config = properties.getAuth().getPasskeys();
        Set<String> origins = new LinkedHashSet<>(config.getAllowedOrigins());
        if (origins.isEmpty()) {
            origins.add(properties.getBaseUrl());
        }
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(userEntities, credentials,
                PublicKeyCredentialRpEntity.builder().id(config.getRpId()).name(config.getRpName()).build(), origins);
        operations.setCustomizeCreationOptions(builder -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return;
            }
            JpaPublicKeyCredentialUserEntityRepository.parseUserId(authentication.getName()).ifPresent(userId ->
                    displayEntity(passkeys, userId, builder));
        });
        return operations;
    }

    private static void displayEntity(PasskeyService passkeys, UUID userId,
                                      org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions
                                              .PublicKeyCredentialCreationOptionsBuilder builder) {
        passkeys.findUserEntityByUserId(userId).ifPresent(entity -> passkeys.findActiveUser(userId).ifPresent(user ->
                builder.user(ImmutablePublicKeyCredentialUserEntity.builder()
                        .id(Bytes.fromBase64(entity.getHandle()))
                        .name(user.getEmail())
                        .displayName(user.getDisplayName())
                        .build())));
    }
}
