package network.foodsharing.testsupport;

import java.util.Optional;
import network.foodsharing.common.error.ApiErrorResponseWriter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.SecurityConfig;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionAuthenticator;
import network.foodsharing.security.SessionCookieWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * For {@code @WebMvcTest}: the real security chain (CSRF, JSON 401/403), the real current-actor/permission services
 * and a session authenticator that knows no cookies (authenticate requests with {@link WithSeedUser} or
 * {@link TestAuth#as}). Also fixes the clock.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
@Import({SecurityConfig.class, CurrentActorService.class, PermissionService.class, SessionCookieWriter.class,
        ApiErrorResponseWriter.class, FixedClockConfig.class})
public class SecurityTestConfig {

    @Bean
    @Primary
    public SessionAuthenticator sessionAuthenticator() {
        return rawToken -> Optional.empty();
    }
}
