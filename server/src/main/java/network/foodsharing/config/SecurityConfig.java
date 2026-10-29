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

    /** A58: browser features the app does not use are switched off; geolocation for "near me" on the map. */
    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), payment=(), usb=(), geolocation=(self)";

    public static final String[] PUBLIC_API = {
            "/api/public/**",
            "/api/auth/session",
            "/api/auth/logout",
            "/api/auth/magic-link",
            "/api/auth/magic-link/verify"
    };

    /** Login endpoints of the frameworks (Google OAuth2, passkey login). */
    public static final String[] FRAMEWORK_ENDPOINTS = {"/oauth2/**", "/login/oauth2/**", "/login/webauthn",
            // A44: OAuth provider (token/userinfo authenticate by client secret or bearer token; authorize checks the cookie itself)
            "/oauth/authorize", "/oauth/token", "/oauth/userinfo", "/.well-known/**",
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
                                                   ObjectProvider<HttpSecurityCustomizer> customizers,
                                                   ObjectProvider<CspFrameSources> frameSources) throws Exception {
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
                        .ignoringRequestMatchers("/api/public/telegram/webhook/**", "/api/public/mailbox/inbound/**",
                                // A44: OAuth clients post form-encoded without cookies
                                "/oauth/token", "/oauth/userinfo"))
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
                        // A41: public images (user photos, FSP/basket/blog pictures); UploadService decides per upload
                        .requestMatchers(HttpMethod.GET, "/api/uploads/*").permitAll()
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
                .headers(headers -> {
                    headers
                            .frameOptions(frame -> frame.sameOrigin())
                            .contentTypeOptions(contentType -> { })
                            .referrerPolicy(referrer -> referrer.policy(
                                    org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                            .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                            .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                    "Permissions-Policy", PERMISSIONS_POLICY));
                    // null only with a mocked AppProperties (web slice tests)
                    AppProperties.Security security = properties.getSecurity() == null
                            ? new AppProperties.Security() : properties.getSecurity();
                    long hsts = security.getHstsMaxAgeSeconds();
                    if (hsts > 0) {
                        // A58: only on HTTPS requests (X-Forwarded-Proto via server.forward-headers-strategy)
                        headers.httpStrictTransportSecurity(sts -> sts.includeSubDomains(true).maxAgeInSeconds(hsts));
                    } else {
                        headers.httpStrictTransportSecurity(sts -> sts.disable());
                    }
                    if (security.getCsp().isEnabled()) {
                        ContentSecurityPolicy.Template policy = ContentSecurityPolicy.build(security.getCsp());
                        // the dev-only fake OIDC/OAuth pages (local/e2e) are left out; frame-src follows the
                        // runtime configuration (donation iframes, G4)
                        headers.addHeaderWriter((request, response) -> {
                            if (!request.getRequestURI().startsWith("/dev/")
                                    && !response.containsHeader("Content-Security-Policy")) {
                                response.setHeader("Content-Security-Policy",
                                        policy.header(ContentSecurityPolicy.frameUrls(frameSources)));
                            }
                        });
                    }
                })
                .addFilterBefore(new SessionCookieAuthenticationFilter(sessionAuthenticator, sessionCookieWriter),
                        AnonymousAuthenticationFilter.class);
        for (HttpSecurityCustomizer customizer : customizers.orderedStream().toList()) {
            customizer.customize(http);
        }
        return http.build();
    }
}
