package network.foodsharing.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import network.foodsharing.common.jobs.JobRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D19.3: every metric used by {@code server/deploy/monitoring/alerts.yml} and {@code grafana-dashboard.json} exists in
 * a real {@code /actuator/prometheus} scrape, the files are well-formed, and the operations events reach the scrape.
 */
@SpringBootTest(properties = {"management.prometheus.metrics.export.enabled=true", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricsContractTest {

    private static final Path MONITORING = Path.of("deploy", "monitoring");
    private static final Pattern METRIC = Pattern.compile(
            "\\b((?:fs|http_server_requests|jvm|hikaricp|process|tomcat)_[a-z0-9_]+)\\b");
    /** Provided by Prometheus itself, not by the application. */
    private static final Set<String> SYNTHETIC = Set.of("up");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JobRunner jobs;

    @Test
    void alertsAndDashboardUseOnlyExportedMetrics() throws Exception {
        mvc.perform(get("/api/public/app-info")).andExpect(status().isOk());
        Set<String> exported = scrapeNames();

        Set<String> used = new TreeSet<>();
        used.addAll(metricNames(Files.readString(MONITORING.resolve("alerts.yml"), StandardCharsets.UTF_8)));
        used.addAll(metricNames(Files.readString(MONITORING.resolve("grafana-dashboard.json"), StandardCharsets.UTF_8)));
        assertThat(used).contains("fs_outbox_pending", "fs_circuit_breaker_state", "fs_job_last_success_timestamp_seconds",
                "fs_rate_limit_rejections_total", "fs_login_failures_total", "http_server_requests_seconds_bucket");
        used.removeAll(SYNTHETIC);
        assertThat(exported).containsAll(used);
    }

    @Test
    @SuppressWarnings("unchecked")
    void alertRulesAreWellFormed() throws Exception {
        Map<String, Object> root = new Yaml().load(Files.readString(MONITORING.resolve("alerts.yml")));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) root.get("groups");
        List<Map<String, Object>> rules = groups.stream()
                .flatMap(g -> ((List<Map<String, Object>>) g.get("rules")).stream()).toList();
        assertThat(rules).hasSizeGreaterThan(10).allSatisfy(rule -> {
            assertThat(rule.get("alert")).isInstanceOf(String.class);
            assertThat(rule.get("expr")).isInstanceOf(String.class);
            assertThat(((Map<String, Object>) rule.get("labels")).get("severity")).isIn("page", "ticket");
            assertThat(((Map<String, Object>) rule.get("annotations")).get("summary")).isNotNull();
        });
        assertThat(rules.stream().map(r -> r.get("alert")).collect(Collectors.toSet())).hasSameSizeAs(rules);
        // every alert is documented in docs/monitoring.md
        String doc = Files.readString(Path.of("..", "docs", "monitoring.md"), StandardCharsets.UTF_8);
        rules.forEach(rule -> assertThat(doc).as("docs/monitoring.md: " + rule.get("alert"))
                .contains("`" + rule.get("alert") + "`"));

        JsonNode dashboard = JsonMapper.builder().build()
                .readTree(Files.readString(MONITORING.resolve("grafana-dashboard.json")));
        assertThat(dashboard.path("panels").size()).isGreaterThan(10);
    }

    @Test
    void operationsEventsReachTheScrape() throws Exception {
        assertThat(jobs.jobs()).isNotEmpty();
        String job = jobs.jobs().getFirst().jobName();
        OperationsMetrics.jobFinished(job, Duration.ofMillis(250), true, Instant.ofEpochSecond(1_800_000_000L));
        OperationsMetrics.rateLimited("support");
        OperationsMetrics.loginFailed("invalid-token");
        OperationsMetrics.deliveryFailed("push");

        String scrape = scrape();
        assertThat(scrape).containsPattern("fs_job_last_success_timestamp_seconds\\{[^}]*name=\"" + job
                + "\"[^}]*} (1\\.8E9|1800000000(\\.0)?)\\b");
        assertThat(scrape).containsPattern("fs_job_duration_seconds_count\\{[^}]*name=\"" + job
                + "\"[^}]*outcome=\"OK\"[^}]*} [1-9]");
        assertThat(scrape).containsPattern("fs_rate_limit_rejections_total\\{[^}]*limiter=\"support\"[^}]*} [1-9]");
        assertThat(scrape).containsPattern("fs_login_failures_total\\{[^}]*reason=\"invalid-token\"[^}]*} [1-9]");
        assertThat(scrape).containsPattern("fs_notification_delivery_failures_total\\{[^}]*channel=\"push\"[^}]*} [1-9]");
        assertThat(scrape).containsPattern("fs_circuit_breaker_state\\{[^}]*integration=\"SMTP\"[^}]*} 0");
        assertThat(scrape).contains("fs_outbox_pending ", "fs_websocket_connected_users ", "fs_async_jobs_running ");
    }

    private String scrape() throws Exception {
        return mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private Set<String> scrapeNames() throws Exception {
        Set<String> names = new TreeSet<>();
        for (String line : scrape().split("\\R")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                names.add(line.split("[{ ]", 2)[0]);
            }
        }
        return names;
    }

    private static Set<String> metricNames(String text) {
        Set<String> names = new TreeSet<>();
        Matcher m = METRIC.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
