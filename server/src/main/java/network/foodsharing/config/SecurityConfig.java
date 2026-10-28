package network.foodsharing.config;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.security.HttpSecurityCustomizer;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.security.SessionCookieAuthenticationFilter;
import network.foodsharing.security.SessionCookieWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Main security chain (docs/conventions.md A9): cookie sessions ({@code fs_session}), CSRF via the
 * {@code XSRF-TOKEN} cookie + {@code X-XSRF-TOKEN} header, JSON 401/403 bodies. Everything under {@code /api/**}
 * needs a session except the public/auth bootstrap endpoints; non-API paths (SPA, static files) are open.
 * Google OAuth2 login and WebAuthn are added to this chain by the auth module through {@link HttpSecurityCustomizer}
 * beans. The security context is never stored in the servlet session: {@code JSESSIONID} only carries the
 * OAuth2/WebAuthn handshakes, never API authentication.
 */
@Configuration
public class SecurityConfig {

    public static final String[] PUBLIC_API = {
            "/api/public/**",
            "/api/auth/session",
            "/api/auth/logout",
            "/api/auth/magic-link",
            "/api/auth/magic-link/verify"
    };

    /** Login endpoints of the frameworks (Google OAuth2, passkey login). */
    public static final String[] FRAMEWORK_ENDPOINTS = {"/oauth2/**", "/login/oauth2/**", "/login/webauthn",
            "/webauthn/authenticate/options"};

    /** Passkey registration (options + finish) needs a session. */
    public static final String[] AUTHENTICATED_FRAMEWORK_ENDPOINTS = {"/webauthn/register", "/webauthn/register/**"};

    @Bean
    @Order(10)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionAuthenticator sessionAuthenticator,
                                                   SessionCookieWriter sessionCookieWriter,
                                                   ApiErrorResponseWriter errorWriter,
                                                   AppProperties properties,
                                                   ObjectProvider<HttpSecurityCustomizer> customizers) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(properties.getAuth().isCookieSecure())
                .sameSite("Lax")
                .path("/"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Load the token on every request so the XSRF-TOKEN cookie is always present for the SPA.
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler)
                        // Telegram and the mail provider call their webhooks server-to-server (secret path, no session, no cookies).
                        .ignoringRequestMatchers("/api/public/telegram/webhook/**", "/api/public/mailbox/inbound/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ORGA")
                        .requestMatchers(PUBLIC_API).permitAll()
                        // Passkeys are deleted via DELETE /api/auth/passkeys/{id} (last-method guard + history).
                        .requestMatchers(HttpMethod.DELETE, "/webauthn/register/**").denyAll()
                        .requestMatchers(AUTHENTICATED_FRAMEWORK_ENDPOINTS).authenticated()
                        .requestMatchers(FRAMEWORK_ENDPOINTS).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // A32: the realtime handshake needs a valid session cookie (401 otherwise).
                        .requestMatchers("/ws", "/ws/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(response, 401,
                                ApiErrorCode.AUTHENTICATION_REQUIRED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(response, 403,
                                ApiErrorCode.ACCESS_DENIED, "Access denied")))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentTypeOptions(contentType -> { })
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .addFilterBefore(new SessionCookieAuthenticationFilter(sessionAuthenticator, sessionCookieWriter),
                        AnonymousAuthenticationFilter.class);
        for (HttpSecurityCustomizer customizer : customizers.orderedStream().toList()) {
            customizer.customize(http);
        }
        return http.build();
    }
}
