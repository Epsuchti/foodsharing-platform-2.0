package network.foodsharing.admin.web;

import java.util.List;
import network.foodsharing.admin.service.AdminOperationsService;
import network.foodsharing.api.AdminJobsApi;
import network.foodsharing.api.model.JobInfo;
import network.foodsharing.api.model.JobOutcome;
import network.foodsharing.api.model.JobRun;
import network.foodsharing.api.model.JobTrigger;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.security.CurrentActorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Scheduled jobs: last run, lock state, run now (ORGA, A25). */
@RestController
public class AdminJobsController implements AdminJobsApi {

    private final AdminOperationsService operations;
    private final CurrentActorService currentActor;

    public AdminJobsController(AdminOperationsService operations, CurrentActorService currentActor) {
        this.operations = operations;
        this.currentActor = currentActor;
    }

    @Override
    public ResponseEntity<List<JobInfo>> listAdminJobs() {
        currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(operations.jobs().stream().map(job -> new JobInfo().name(job.name())
                .cron(job.schedule()).locked(job.locked()).lastRun(job.lastRun().map(AdminJobsController::toApi)
                        .orElse(null))).toList());
    }

    @Override
    public ResponseEntity<JobRun> runAdminJob(String name) {
        var principal = currentActor.requireRole(Role.ORGA);
        return ResponseEntity.ok(toApi(operations.runNow(name, principal.userId())));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.JobRunPage> listAdminJobRuns(String name, Integer page,
                                                                                     Integer size) {
        currentActor.requireRole(Role.ORGA);
        var runs = operations.runs(name, page == null ? 0 : page, size == null ? 20 : size);
        return ResponseEntity.ok(new network.foodsharing.api.model.JobRunPage()
                .items(runs.getContent().stream().map(AdminJobsController::toApi).toList())
                .page(network.foodsharing.common.mapper.PageMapper.toPageMeta(runs)));
    }

    static JobRun toApi(network.foodsharing.common.jobs.JobRun run) {
        return new JobRun().jobName(run.getJobName()).startedAt(TimeMapper.toOffset(run.getStartedAt()))
                .finishedAt(run.getFinishedAt() == null ? null : TimeMapper.toOffset(run.getFinishedAt()))
                .outcome(JobOutcome.valueOf(run.getOutcome().name())).message(run.getMessage())
                .trigger(JobTrigger.valueOf(run.getTrigger().name()));
    }
}
