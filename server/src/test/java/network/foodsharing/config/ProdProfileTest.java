package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** D19.1: operational settings of the prod profile; local, e2e and test keep health on the application port. */
class ProdProfileTest {

    @Test
    void managementPortOnlyInProd() throws IOException {
        assertThat(load("application-prod.properties").getProperty("management.server.port"))
                .isEqualTo("${MANAGEMENT_PORT:9090}");
        for (String other : new String[] {"application.properties", "application-local.properties",
                "application-e2e.properties", "application-test.properties"}) {
            assertThat(load(other).getProperty("management.server.port")).as(other).isNull();
        }
    }

    @Test
    void prodShutsDownGracefullyAndBoundsThePool() throws IOException {
        Properties prod = load("application-prod.properties");
        assertThat(prod.getProperty("server.shutdown")).isEqualTo("graceful");
        assertThat(prod.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
        assertThat(prod.getProperty("spring.datasource.hikari.maximum-pool-size")).isNotBlank();
        assertThat(prod.getProperty("management.endpoint.health.group.readiness.include")).contains("db");
    }

    @Test
    void prodNeverEnablesDevFeaturesOrSeedData() throws IOException {
        Properties prod = load("application-prod.properties");
        assertThat(prod.getProperty("spring.liquibase.contexts")).isEqualTo("schema");
        assertThat(prod.getProperty("app.dev.enabled")).isNull();
        assertThat(load("application.properties").getProperty("app.dev.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("app.oauth.allow-generated-key")).isEqualTo("false");
    }

    static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (var in = new ClassPathResource(name).getInputStream()) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
