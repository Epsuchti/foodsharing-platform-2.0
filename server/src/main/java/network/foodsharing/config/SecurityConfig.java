package network.foodsharing.config;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.security.SessionCookieAuthenticationFilter;
import network.foodsharing.security.SessionCookieWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Main security chain (docs/conventions.md A9): cookie sessions ({@code fs_session}), CSRF via the
 * {@code XSRF-TOKEN} cookie + {@code X-XSRF-TOKEN} header, JSON 401/403 bodies. Everything under {@code /api/**}
 * needs a session except the public/auth bootstrap endpoints; non-API paths (SPA, static files) are open.
 * Google OAuth2 login and WebAuthn are added to this chain by the auth module (T4).
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

    public static final String[] FRAMEWORK_ENDPOINTS = {"/oauth2/**", "/login/oauth2/**", "/login/webauthn", "/webauthn/**"};

    @Bean
    @Order(10)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionAuthenticator sessionAuthenticator,
                                                   SessionCookieWriter sessionCookieWriter,
                                                   ApiErrorResponseWriter errorWriter,
                                                   AppProperties properties) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie
                .secure(properties.getAuth().isCookieSecure())
                .sameSite("Lax")
                .path("/"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // Load the token on every request so the XSRF-TOKEN cookie is always present for the SPA.
        csrfHandler.setCsrfRequestAttributeName(null);

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))
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
                        .requestMatchers(FRAMEWORK_ENDPOINTS).permitAll()
                        .requestMatchers("/api/**").authenticated()
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
                        AnonymousAuthenticationFilter.class)
                .build();
    }
}
