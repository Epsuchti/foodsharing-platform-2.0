package network.foodsharing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local/e2e only: the dev API (seed users, login-as, captured mails, clock), the fake OIDC provider and the H2
 * console are open and CSRF-exempt. The beans behind these paths exist only in these profiles too.
 */
@Configuration
@Profile({"local", "e2e"})
public class DevSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/dev/**", "/dev/oidc/**", "/h2-console/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .build();
    }
}
