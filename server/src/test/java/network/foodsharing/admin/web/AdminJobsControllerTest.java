package network.foodsharing.admin.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import network.foodsharing.admin.service.AdminOperationsService;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.error.ApiExceptionHandler;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.testsupport.SecurityTestConfig;
import network.foodsharing.testsupport.SeedUsers;
import network.foodsharing.testsupport.WithSeedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** C15 / D6.3 (A25): the job list, "run now" and the runs of a job are ORGA only; an unknown job is 404 JOB_UNKNOWN. */
@WebMvcTest(AdminJobsController.class)
@Import({SecurityTestConfig.class, ApiExceptionHandler.class})
@ActiveProfiles("test")
class AdminJobsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminOperationsService operations;

    private static JobRun manualRun() {
        JobRun run = new JobRun();
        run.setJobName("cleanup");
        run.setStartedAt(Instant.parse("2026-11-16T03:00:00Z"));
        run.setFinishedAt(Instant.parse("2026-11-16T03:00:01Z"));
        run.setOutcome(JobRun.Outcome.OK);
        run.setTrigger(JobRun.Trigger.MANUAL);
        run.setActorId(SeedUsers.ORGA.id());
        return run;
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/admin/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithSeedUser("foodsaver")
    void othersAre403() throws Exception {
        mvc.perform(get("/api/admin/jobs")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/jobs/cleanup/run").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/jobs/cleanup/runs")).andExpect(status().isForbidden());
        verify(operations, never()).runNow(any(), any());
    }

    @Test
    @WithSeedUser("orga")
    void orgaListsRunsAndReadsTheRuns() throws Exception {
        when(operations.jobs()).thenReturn(List.of(
                new AdminOperationsService.JobView("cleanup", "0 30 3 * * *", Optional.of(manualRun()), false)));
        mvc.perform(get("/api/admin/jobs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("cleanup"))
                .andExpect(jsonPath("$[0].cron").value("0 30 3 * * *"))
                .andExpect(jsonPath("$[0].locked").value(false))
                .andExpect(jsonPath("$[0].lastRun.outcome").value("OK"));

        when(operations.runNow(eq("cleanup"), eq(SeedUsers.ORGA.id()))).thenReturn(manualRun());
        mvc.perform(post("/api/admin/jobs/cleanup/run").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("cleanup"))
                .andExpect(jsonPath("$.outcome").value("OK"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"));

        when(operations.runs("cleanup", 0, 5)).thenReturn(new PageImpl<>(List.of(manualRun()), PageRequest.of(0, 5), 1));
        mvc.perform(get("/api/admin/jobs/cleanup/runs").param("size", "5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].trigger").value("MANUAL"))
                .andExpect(jsonPath("$.page.size").value(5));
    }

    @Test
    @WithSeedUser("orga")
    void unknownJobIs404() throws Exception {
        when(operations.runNow(eq("no-such-job"), any())).thenThrow(ApiException.notFound(ApiErrorCode.JOB_UNKNOWN));
        when(operations.runs(eq("no-such-job"), any(Integer.class), any(Integer.class)))
                .thenThrow(ApiException.notFound(ApiErrorCode.JOB_UNKNOWN));
        mvc.perform(post("/api/admin/jobs/no-such-job/run").with(csrf())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_UNKNOWN"));
        mvc.perform(get("/api/admin/jobs/no-such-job/runs")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_UNKNOWN"));
    }
}
