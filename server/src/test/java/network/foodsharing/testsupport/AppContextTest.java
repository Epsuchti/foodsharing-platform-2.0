package network.foodsharing.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * The whole application on the {@code test} profile (empty H2, no seed, no scheduling) with MockMvc and the Prometheus
 * export that production has (Spring Boot tests switch it off). The one full context of the unit phase: every
 * {@code @SpringBootTest} that needs nothing else uses this, so it starts once per fork (docs/conventions.md A60).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = {"management.prometheus.metrics.export.enabled=true", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface AppContextTest {
}
