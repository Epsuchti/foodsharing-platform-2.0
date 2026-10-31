package network.foodsharing.common.jobs;

import org.springframework.context.ApplicationEventPublisher;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.javacrumbs.shedlock.core.ClockProvider;
import network.foodsharing.common.jobs.repository.JobRunRepository;
import network.foodsharing.common.metrics.OperationsMetrics;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wraps every job run, scheduled or manual (A25): a {@code job_run} row (RUNNING → OK/FAILED), a log line and the
 * last run per job. Rows are written in their own transactions, so a failing job still leaves its FAILED row.
 * Scheduled runs rethrow nothing (the scheduler would only log it again); manual runs rethrow.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final int MAX_MESSAGE = 2000;

    private final JobRunRepository runs;
    private final TransactionTemplate tx;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ObjectProvider<RunnableJob> jobs;
    private final Map<String, JobRun> lastInMemory = new ConcurrentHashMap<>();
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    /** Published when a job failed twice in a row (once per failure streak): ORGA bell AUTOMATION_RUN_FAILED. */
    public record JobFailedEvent(String jobName, int consecutiveFailures, String message) {
    }

    public JobRunner(JobRunRepository runs, PlatformTransactionManager transactionManager, JdbcClient jdbc, Clock clock,
            ObjectProvider<RunnableJob> jobs, ApplicationEventPublisher events) {
        this.events = events;
        this.runs = runs;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbc = jdbc;
        this.clock = clock;
        this.jobs = jobs;
    }

    /** Every job gets its metrics ({@code fs_job_*}) at startup, before its first run (D19.3). */
    @EventListener(ApplicationReadyEvent.class)
    public void registerMetrics() {
        jobs.forEach(job -> OperationsMetrics.registerJob(job.jobName()));
    }

    /** All jobs, sorted by name. */
    public List<RunnableJob> jobs() {
        return jobs.stream().sorted(Comparator.comparing(RunnableJob::jobName)).toList();
    }

    public Optional<RunnableJob> find(String name) {
        return jobs.stream().filter(job -> job.jobName().equals(name)).findFirst();
    }

    /**
     * Jobs that run on their schedule ({@code app.jobs.scheduled}, comma separated); empty (the default) = every job.
     * The e2e profile keeps only the infrastructure jobs (mail outbox, search refresh, Telegram poll): the business
     * jobs ran on the machine's wall clock (automation every 5 min, hourly reminders, nightly maintenance) and
     * changed the data in the middle of a test (a PICKUP_DECISION history entry appearing in D10.3). Tests start
     * them explicitly ({@code POST /api/dev/jobs/{name}/run}, a MANUAL run, never skipped).
     */
    private java.util.Set<String> scheduledJobs = java.util.Set.of();

    @org.springframework.beans.factory.annotation.Value("${app.jobs.scheduled:}")
    void setScheduledJobs(String names) {
        scheduledJobs = java.util.Arrays.stream(names.split(",")).map(String::strip).filter(n -> !n.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Whether a SCHEDULE run of {@code jobName} executes (see {@link #setScheduledJobs}). */
    boolean runsOnSchedule(String jobName) {
        return scheduledJobs.isEmpty() || scheduledJobs.contains(jobName);
    }

    /** Runs {@code job} now and records it. Manual runs ({@code actorId} = the ORGA user or null in dev) rethrow. */
    public JobRun run(RunnableJob job, JobRun.Trigger trigger, UUID actorId) {
        if (trigger == JobRun.Trigger.SCHEDULE && !runsOnSchedule(job.jobName())) {
            // not scheduled in this profile: nothing runs and nothing is recorded (the row is returned unsaved)
            return newRow(job, trigger, actorId);
        }
        boolean record = trigger == JobRun.Trigger.MANUAL || job.recordEveryRun();
        JobRun started = newRow(job, trigger, actorId);
        JobRun run = record ? tx.execute(status -> runs.save(started)) : started;
        long startNanos = System.nanoTime();
        RuntimeException failure = null;
        try {
            job.runNow();
            run.setOutcome(JobRun.Outcome.OK);
        } catch (RuntimeException ex) {
            failure = ex;
            run.setOutcome(JobRun.Outcome.FAILED);
            run.setMessage(truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage()));
            log.warn("Job {} ({}) failed", job.jobName(), trigger, ex);
        }
        run.setFinishedAt(clock.instant());
        OperationsMetrics.jobFinished(job.jobName(), Duration.ofNanos(System.nanoTime() - startNanos),
                failure == null, run.getFinishedAt());
        if (record || failure != null) {
            JobRun finished = run;
            run = tx.execute(status -> runs.save(record ? finished : copy(finished)));
        }
        lastInMemory.put(job.jobName(), run);
        trackFailures(job.jobName(), failure == null ? null : run.getMessage());
        log.debug("Job {} ({}) finished: {}", job.jobName(), trigger, run.getOutcome());
        if (failure != null && trigger == JobRun.Trigger.MANUAL) {
            throw failure;
        }
        return run;
    }

    private void trackFailures(String jobName, String failureMessage) {
        if (failureMessage == null) {
            consecutiveFailures.remove(jobName);
            return;
        }
        int count = consecutiveFailures.merge(jobName, 1, Integer::sum);
        if (count == 2) {
            try {
                events.publishEvent(new JobFailedEvent(jobName, count, failureMessage));
            } catch (RuntimeException ex) {
                log.warn("Job failure alert for {} failed: {}", jobName, ex.toString());
            }
        }
    }

    /** Last run of a job: newest of the recorded row and the in-memory one. */
    /** Run history of a job, newest first. */
    public org.springframework.data.domain.Page<JobRun> runs(String jobName, int page, int size) {
        return runs.findByJobNameOrderByStartedAtDescIdDesc(jobName,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
    }

    public Optional<JobRun> lastRun(String jobName) {
        Optional<JobRun> stored = runs.findFirstByJobNameOrderByStartedAtDescIdDesc(jobName);
        JobRun memory = lastInMemory.get(jobName);
        if (memory == null) {
            return stored;
        }
        if (stored.isEmpty() || stored.get().getStartedAt().isBefore(memory.getStartedAt())) {
            return Optional.of(memory);
        }
        return stored;
    }

    /** Whether a ShedLock lock on {@code jobName} is currently held (ShedLock compares with the real time). */
    public boolean isLocked(String jobName) {
        return jdbc.sql("select count(*) from shedlock where name = :name and lock_until > :now")
                .param("name", jobName)
                .param("now", Timestamp.from(ClockProvider.now()))
                .query(Long.class).single() > 0;
    }

    /** Deletes {@code job_run} rows started before {@code before} (cleanup job). */
    public int purgeBefore(Instant before) {
        Integer deleted = tx.execute(status -> runs.deleteStartedBefore(before));
        return deleted == null ? 0 : deleted;
    }

    private JobRun newRow(RunnableJob job, JobRun.Trigger trigger, UUID actorId) {
        JobRun row = new JobRun();
        row.setJobName(job.jobName());
        row.setStartedAt(clock.instant());
        row.setOutcome(JobRun.Outcome.RUNNING);
        row.setTrigger(trigger);
        row.setActorId(actorId);
        return row;
    }

    private static JobRun copy(JobRun run) {
        JobRun row = new JobRun();
        row.setJobName(run.getJobName());
        row.setStartedAt(run.getStartedAt());
        row.setFinishedAt(run.getFinishedAt());
        row.setOutcome(run.getOutcome());
        row.setMessage(run.getMessage());
        row.setTrigger(run.getTrigger());
        row.setActorId(run.getActorId());
        return row;
    }

    private static String truncate(String text) {
        return text.length() <= MAX_MESSAGE ? text : text.substring(0, MAX_MESSAGE);
    }
}
