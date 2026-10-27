package network.foodsharing.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import network.foodsharing.common.error.ApiExceptionHandler;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.WithSeedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/jobs/{id} and /result: owner only (404 otherwise), 409 while the result is not ready. */
@WebMvcTest(JobsController.class)
@Import({SecurityTestConfig.class, ApiExceptionHandler.class, ProgressTracker.class})
@ActiveProfiles("test")
class JobsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProgressTracker tracker;

    @Test
    @WithSeedUser("bot")
    void ownerSeesProgressAndDownloadsTheResult() throws Exception {
        UUID job = tracker.start(SeedUsers.BOT.id(), 10);
        tracker.progress(job, 4);

        mvc.perform(get("/api/jobs/" + job))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.done").value(4))
                .andExpect(jsonPath("$.resultAvailable").value(false));
        mvc.perform(get("/api/jobs/" + job + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_RESULT_NOT_AVAILABLE"));

        tracker.complete(job, new ProgressTracker.JobResult("%PDF-1.7".getBytes(StandardCharsets.US_ASCII),
                "application/pdf", "passports.pdf"));
        mvc.perform(get("/api/jobs/" + job))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.done").value(10))
                .andExpect(jsonPath("$.resultAvailable").value(true))
                .andExpect(jsonPath("$.resultContentType").value("application/pdf"));
        mvc.perform(get("/api/jobs/" + job + "/result"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"passports.pdf\""))
                .andExpect(content().string("%PDF-1.7"));
    }

    @Test
    @WithSeedUser("welcome")
    void otherUsersAndUnknownJobsAre404() throws Exception {
        UUID job = tracker.start(SeedUsers.BOT.id(), 1);
        mvc.perform(get("/api/jobs/" + job))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        mvc.perform(get("/api/jobs/" + job + "/result")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithSeedUser("bot")
    void failedJobsReportTheirMessage() throws Exception {
        UUID job = tracker.runNow(SeedUsers.BOT.id(), 3, progress -> {
            progress.increment();
            throw new IllegalStateException("renderer failed");
        });
        mvc.perform(get("/api/jobs/" + job))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.message").value("renderer failed"));
        assertThat(tracker.find(job)).isPresent();
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/jobs/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
