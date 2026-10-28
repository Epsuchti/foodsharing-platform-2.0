package network.foodsharing.dev.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Dev API for M2 tests: persisted notifications, running jobs by name, the iCal fixture (local/e2e only). */
@Service
@Profile({"local", "e2e"})
public class DevNotificationService {

    /** A bell with its parameters. */
    public record DevBell(Bell bell, Map<String, String> params) {
    }

    private final BellService bells;
    private final List<RunnableJob> jobs;
    private final JobRunner jobRunner;

    public DevNotificationService(BellService bells, List<RunnableJob> jobs, JobRunner jobRunner) {
        this.jobRunner = jobRunner;
        this.bells = bells;
        this.jobs = jobs;
    }

    /** Bells (incl. deleted ones) newest first, optionally filtered by user and type name. */
    public List<DevBell> notifications(UUID userId, String type) {
        NotificationType notificationType = null;
        if (type != null && !type.isBlank()) {
            try {
                notificationType = NotificationType.valueOf(type);
            } catch (IllegalArgumentException e) {
                return List.of();
            }
        }
        return bells.search(userId, notificationType).stream().map(bell -> new DevBell(bell, bells.params(bell)))
                .toList();
    }

    /** @throws ApiException 404 DEV_JOB_NOT_FOUND */
    public void runJob(String name) {
        RunnableJob job = jobs.stream().filter(candidate -> candidate.jobName().equals(name)).findFirst()
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.DEV_JOB_NOT_FOUND));
        jobRunner.run(job, JobRun.Trigger.MANUAL, null);
    }

    public List<String> jobNames() {
        return jobs.stream().map(RunnableJob::jobName).sorted().toList();
    }

    /** Static calendar used by the guide iCal import e2e tests ({@code dev/ical-fixture.ics}). */
    public String icalFixture() {
        try (var in = new ClassPathResource("dev/ical-fixture.ics").getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("dev/ical-fixture.ics missing", e);
        }
    }
}
