package network.foodsharing.history.internal;

import java.util.List;
import network.foodsharing.history.service.HistoryService;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.jpa.boot.spi.JpaSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hands the {@link HistoryIntegrator} to Hibernate through {@code hibernate.integrator_provider}, so every Spring
 * context's EntityManagerFactory gets a listener bound to that context's {@link HistoryService} (a
 * META-INF/services integrator would need a static bean holder, which breaks with several cached test contexts).
 */
@Configuration
public class HistoryHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer historyIntegratorCustomizer(ObjectProvider<HistoryService> historyService) {
        TrackedFieldListener listener = new TrackedFieldListener(historyService);
        return properties -> properties.put(JpaSettings.INTEGRATOR_PROVIDER,
                (IntegratorProvider) () -> List.of(new HistoryIntegrator(listener)));
    }
}
