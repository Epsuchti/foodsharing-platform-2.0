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
                "application-e2e.properties", "application-test.properties", "application-staging.properties"}) {
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

    /** Decision 173: prod alone has no test-environment settings; staging adds them on top of prod. */
    @Test
    void stagingExtrasLiveOnlyInTheStagingFile() throws IOException {
        Properties prod = load("application-prod.properties");
        assertThat(prod.stringPropertyNames()).noneMatch(key -> key.startsWith("app.staging."));
        assertThat(prod.getProperty("app.demo-banner")).isNull();
        assertThat(prod.getProperty("app.mail.recipient-policy")).isNull();
        assertThat(prod.getProperty("app.mail.allowed-recipients")).isNull();

        Properties defaults = load("application.properties");
        assertThat(defaults.getProperty("app.staging.persona-panel.password")).isEmpty();
        assertThat(defaults.getProperty("app.demo-banner")).isEqualTo("false");
        assertThat(defaults.getProperty("app.mail.recipient-policy")).isEqualTo("all");

        Properties staging = load("application-staging.properties");
        assertThat(staging.getProperty("spring.liquibase.contexts")).isEqualTo("schema,seed");
        assertThat(staging.getProperty("app.dev.enabled")).isNull();
        assertThat(staging.getProperty("spring.h2.console.enabled")).isNull();
        assertThat(staging.getProperty("app.staging.persona-panel.password")).isEqualTo("${PERSONA_PANEL_PASSWORD:}");
        assertThat(staging.getProperty("app.demo-banner")).isEqualTo("true");
        assertThat(staging.getProperty("app.mail.recipient-policy")).isEqualTo("staging");
        assertThat(staging.getProperty("app.mail.allowed-recipients")).isEqualTo("${MAIL_ALLOWED_RECIPIENTS:}");
        assertThat(staging.getProperty("app.telegram.mode")).isEqualTo("log");
        // everything else comes from prod: no datasource, secrets or ports of its own
        assertThat(staging.stringPropertyNames()).noneMatch(key -> key.startsWith("spring.datasource.")
                || key.startsWith("management.") || key.startsWith("app.auth.") || key.startsWith("server."));
    }

    static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (var in = new ClassPathResource(name).getInputStream()) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
