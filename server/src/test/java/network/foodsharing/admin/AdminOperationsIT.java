package network.foodsharing.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import network.foodsharing.testsupport.SeedUsers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** A25/A26: ORGA job list and run now (job_run + history), integration status and probe; others get 403. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-ops-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class AdminOperationsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    private Cookie login(SeedUsers user) throws Exception {
        var result = mvc.perform(post("/api/dev/login-as").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user.id() + "\"}")).andExpect(status().isOk()).andReturn();
        return Arrays.stream(result.getResponse().getCookies()).filter(c -> c.getName().equals("fs_session"))
                .findFirst().orElseThrow();
    }

    @Test
    void jobsListAndRunNow() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/jobs").cookie(login(SeedUsers.FOODSAVER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/jobs").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(Matchers.hasItems("mail-outbox", "pickup-reminder",
                        "region-statistics", "history-retention")))
                .andExpect(jsonPath("$[?(@.name == 'region-statistics')].locked").value(Matchers.contains(false)));
        mvc.perform(post("/api/admin/jobs/region-statistics/run").cookie(orga).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("OK"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"));
        assertThat(jdbc.queryForObject("select count(*) from job_run where job_name = 'region-statistics' "
                + "and actor_id = ?", Integer.class, SeedUsers.ORGA.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'ADMIN_JOB_RUN_MANUAL'",
                Integer.class)).isEqualTo(1);
        mvc.perform(get("/api/admin/jobs").cookie(orga))
                .andExpect(jsonPath("$[?(@.name == 'region-statistics')].lastRun.outcome").value(Matchers.contains("OK")));
        mvc.perform(post("/api/admin/jobs/nope/run").cookie(orga).with(csrf())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_UNKNOWN"));
        mvc.perform(get("/api/admin/jobs/region-statistics/runs").param("size", "5").cookie(orga))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.items[0].jobName").value("region-statistics"))
                .andExpect(jsonPath("$.page.size").value(5));
        mvc.perform(get("/api/admin/jobs/nope/runs").cookie(orga)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_UNKNOWN"));
        mvc.perform(get("/api/admin/jobs/region-statistics/runs").cookie(login(SeedUsers.FOODSAVER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void integrationsStatusAndProbe() throws Exception {
        Cookie orga = login(SeedUsers.ORGA);
        mvc.perform(get("/api/admin/integrations").cookie(orga)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[?(@.integration == 'SMTP')].mode").value(Matchers.contains("LOG")))
                .andExpect(jsonPath("$[?(@.integration == 'WEB_PUSH')].state").value(Matchers.contains("CLOSED")));
        mvc.perform(post("/api/admin/integrations/GEOCODING/probe").cookie(orga).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CLOSED"));
        mvc.perform(post("/api/admin/integrations/GEOCODING/probe").cookie(login(SeedUsers.BIEB)).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
