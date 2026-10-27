package network.foodsharing.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.security.CurrentActorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Fills created/updated timestamps from the application {@link Clock} and created/updated by from the signed-in user.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware(CurrentActorService currentActorService) {
        return currentActorService::currentUserId;
    }

    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
