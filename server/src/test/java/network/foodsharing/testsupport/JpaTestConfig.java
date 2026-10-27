package network.foodsharing.testsupport;

import network.foodsharing.config.AppProperties;
import network.foodsharing.config.JpaAuditingConfig;
import network.foodsharing.history.internal.HistoryHibernateConfig;
import network.foodsharing.history.service.HistoryActionRegistry;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * For {@code @DataJpaTest} (use {@link JpaSliceTest}): auditing with the fixed clock, the current actor, the history
 * recorder and the {@code @Tracked} Hibernate listener, so repository/service tests see the real history behaviour.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({FixedClockConfig.class, JpaAuditingConfig.class, CurrentActorService.class, PermissionService.class,
        HistoryService.class, HistoryHibernateConfig.class, HistoryActionRegistry.class})
public class JpaTestConfig {
}
