package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * D19.1 / plan §17: with the logging settings of the prod profile every console line is one JSON object with
 * timestamp, level, logger, message, the request's traceId and the app/version fields.
 */
@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @AfterEach
    void restorePlainLogging() {
        // re-initialise the logging system without structured format for the tests that follow in this JVM
        run(Map.of()).close();
    }

    @Test
    void prodLogsOneJsonObjectPerLineWithTraceId(CapturedOutput output) throws Exception {
        Properties prod = ProdProfileTest.load("application-prod.properties");
        Map<String, Object> props = new HashMap<>();
        prod.stringPropertyNames().stream().filter(k -> k.startsWith("logging.structured"))
                .forEach(k -> props.put(k, prod.getProperty(k)));
        props.put("APP_VERSION", "42");
        assertThat(props).containsKey("logging.structured.format.console");

        try (ConfigurableApplicationContext ignored = run(props)) {
            MDC.put("traceId", "abcdef123456");
            try {
                LoggerFactory.getLogger("network.foodsharing.LogProbe").warn("structured probe {}", 1);
            } finally {
                MDC.remove("traceId");
            }
        }

        List<JsonNode> lines = new ArrayList<>();
        for (String line : output.getOut().split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            assertThat(line.strip()).as(line).startsWith("{").endsWith("}");
            lines.add(JSON.readTree(line));
        }
        JsonNode probe = lines.stream().filter(n -> n.path("message").asText().equals("structured probe 1"))
                .findFirst().orElseThrow();
        assertThat(probe.path("level").asText()).isEqualTo("WARN");
        assertThat(probe.path("logger_name").asText()).isEqualTo("network.foodsharing.LogProbe");
        assertThat(probe.path("traceId").asText()).isEqualTo("abcdef123456");
        assertThat(probe.path("@timestamp").asText()).isNotBlank();
        assertThat(probe.path("app").asText()).isEqualTo("foodsharing-platform");
        assertThat(probe.path("version").asText()).isEqualTo("42");
    }

    private static ConfigurableApplicationContext run(Map<String, Object> props) {
        // cached test contexts keep the logging system marked as initialised; reset it so this run configures it
        LoggingSystem.get(StructuredLoggingTest.class.getClassLoader()).cleanUp();
        Map<String, Object> all = new HashMap<>(props);
        all.put("spring.main.banner-mode", "off");
        all.put("spring.config.location", "optional:classpath:/none/");
        return new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE).properties(all)
                .logStartupInfo(false).run();
    }

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }
}
