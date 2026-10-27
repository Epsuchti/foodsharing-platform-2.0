package network.foodsharing.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Lets a feature module add login mechanisms to the main security chain without {@code config} depending on it
 * (the auth module adds Google OAuth2 login and WebAuthn). Beans are applied in {@code @Order} order before the
 * chain is built.
 */
@FunctionalInterface
public interface HttpSecurityCustomizer {

    void customize(HttpSecurity http) throws Exception;
}
