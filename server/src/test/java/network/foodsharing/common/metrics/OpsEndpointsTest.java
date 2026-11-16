package network.foodsharing.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import network.foodsharing.config.AppVersionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AD1 (plan §14.4): the operations endpoints that are open without login: the {@code version} health group and
 * {@code /actuator/info} report the build version that the app-info endpoint shows, Prometheus exposes the HTTP and JVM
 * metrics, and every other actuator endpoint stays closed. Same context as {@link MetricsContractTest} (cached).
 */
@SpringBootTest(properties = {"management.prometheus.metrics.export.enabled=true", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppVersionResolver version;

    @Test
    void versionHealthGroupAndInfoReportTheBuildVersion() throws Exception {
        String expected = version.resolve();
        mvc.perform(get("/api/public/app-info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(expected));
        mvc.perform(get("/actuator/health/version")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.appVersion.details.version").value(expected));
        mvc.perform(get("/actuator/info")).andExpect(status().isOk())
                .andExpect(jsonPath("$.app.version").value(expected));
    }

    @Test
    void prometheusExposesHttpAndJvmMetricsWithoutLogin() throws Exception {
        mvc.perform(get("/api/public/app-info")).andExpect(status().isOk());
        var response = mvc.perform(get("/actuator/prometheus").accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).containsAnyOf("text/plain", "openmetrics");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("http_server_requests_seconds_count", "jvm_memory_used_bytes");
        assertThat(body).containsPattern("uri=\"/api/public/app-info\"");
    }

    @Test
    void otherActuatorEndpointsAreNotPublic() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/loggers"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertThat(status).as(path).isIn(401, 403, 404);
        }
    }
}
