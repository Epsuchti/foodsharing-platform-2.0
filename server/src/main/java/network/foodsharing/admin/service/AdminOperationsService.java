package network.foodsharing.admin.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.admin.domain.AdminAction;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.integration.CircuitBreaker;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.JobRun;
import network.foodsharing.common.jobs.JobRunner;
import network.foodsharing.common.jobs.RunnableJob;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.service.IntegrationTestMessages;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;

/**
 * ORGA operations (A25, A26): scheduled jobs with their last run and "run now" (bypasses the lock), integration
 * breakers with "probe now". Runs are not wrapped in a transaction: {@link JobRunner} writes its own rows.
 */
@Service
public class AdminOperationsService {

    /** One job for the admin page. */
    public record JobView(String name, String schedule, Optional<JobRun> lastRun, boolean locked) {
    }

    private final JobRunner jobs;
    private final IntegrationRegistry integrations;
    private final HistoryService history;
    private final IntegrationTestMessages testMessages;

    public AdminOperationsService(JobRunner jobs, IntegrationRegistry integrations, HistoryService history,
                                  IntegrationTestMessages testMessages) {
        this.testMessages = testMessages;
        this.jobs = jobs;
        this.integrations = integrations;
        this.history = history;
    }

    public List<JobView> jobs() {
        return jobs.jobs().stream().map(job -> new JobView(job.jobName(), job.schedule(),
                jobs.lastRun(job.jobName()), jobs.isLocked(job.jobName()))).toList();
    }

    /** @throws ApiException 404 JOB_UNKNOWN; a failing job rethrows (500) after its FAILED row is written */
    public JobRun runNow(String name, UUID actorId) {
        RunnableJob job = jobs.find(name).orElseThrow(() -> ApiException.notFound(ApiErrorCode.JOB_UNKNOWN));
        history.record(history.event(AdminAction.JOB_RUN_MANUAL, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("job", name)).build());
        return jobs.run(job, JobRun.Trigger.MANUAL, actorId);
    }

    public List<CircuitBreaker.Snapshot> integrations() {
        return integrations.snapshots();
    }

    public IntegrationRegistry.Mode mode(Integration integration) {
        return integrations.mode(integration);
    }

    /** Probes now: closes the breaker when reachable, opens it otherwise. */
    public CircuitBreaker.Snapshot probe(Integration integration) {
        boolean up = integrations.breaker(integration).probeNow();
        history.record(history.event(AdminAction.INTEGRATION_PROBED, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("integration", integration.name(), "reachable", up)).build());
        return integrations.breaker(integration).snapshot();
    }

    /** Sends a test message through the integration (SMTP: mail to me, TELEGRAM: chat, WEB_PUSH: me). */
    public CircuitBreaker.Snapshot sendTest(Integration integration, User caller, String target) {
        testMessages.send(integration, caller, target);
        history.record(history.event(AdminAction.INTEGRATION_TEST_SENT, HistoryService.SYSTEM_SUBJECT_ID)
                .metadata(Map.of("integration", integration.name())).build());
        return integrations.breaker(integration).snapshot();
    }
}
