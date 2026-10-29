package network.foodsharing.common.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import network.foodsharing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory progress of long-running work (batch passports, large CSV imports; docs/conventions.md A17), exposed as
 * {@code GET /api/jobs/{jobId}}. Jobs and their results are kept for {@code app.jobs.result-ttl-minutes} after they
 * finish. Single instance only (like the other jobs until ShedLock arrives in M4).
 *
 * <p>Work submitted with {@link #submit} runs on a virtual thread without the caller's security context or
 * transaction: capture the actor id before and open transactions inside the work.
 */
@Component
public class ProgressTracker {

    private static final Logger log = LoggerFactory.getLogger(ProgressTracker.class);

    public enum Status { RUNNING, DONE, FAILED }

    /** A finished job's file. */
    public record JobResult(byte[] content, String contentType, String fileName) {
    }

    /** Snapshot of a job. */
    public record JobState(UUID id, UUID ownerId, Status status, int total, int done, String message,
                           JobResult result, Instant startedAt, Instant finishedAt) {
    }

    /** Handle passed to the work: report progress. */
    public interface Progress {
        void total(int total);

        void done(int done);

        void increment();
    }

    /** The work of a job; returns the result file or null. */
    @FunctionalInterface
    public interface Work {
        JobResult run(Progress progress) throws Exception;
    }

    private final Map<UUID, JobState> jobs = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;
    private final ExecutorService executor;

    @Autowired
    public ProgressTracker(Clock clock, AppProperties properties) {
        this(clock, Duration.ofMinutes(properties.getJobs().getResultTtlMinutes()),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    ProgressTracker(Clock clock, Duration ttl, ExecutorService executor) {
        this.clock = clock;
        this.ttl = ttl;
        this.executor = executor;
    }

    /** Registers a running job (for work the caller drives itself). */
    public UUID start(UUID ownerId, int total) {
        evictExpired();
        UUID id = UUID.randomUUID();
        jobs.put(id, new JobState(id, ownerId, Status.RUNNING, total, 0, null, null, clock.instant(), null));
        return id;
    }

    /** Starts the work in the background and returns the job id at once. */
    public UUID submit(UUID ownerId, int total, Work work) {
        UUID id = start(ownerId, total);
        executor.execute(() -> runJob(id, work));
        return id;
    }

    /** Runs the work in the calling thread (tests, small inputs) and returns the job id. */
    public UUID runNow(UUID ownerId, int total, Work work) {
        UUID id = start(ownerId, total);
        runJob(id, work);
        return id;
    }

    public void progress(UUID id, int done) {
        jobs.computeIfPresent(id, (key, job) -> new JobState(job.id(), job.ownerId(), job.status(), job.total(),
                Math.min(Math.max(done, 0), Math.max(job.total(), done)), job.message(), job.result(),
                job.startedAt(), job.finishedAt()));
    }

    public void complete(UUID id, JobResult result) {
        jobs.computeIfPresent(id, (key, job) -> new JobState(job.id(), job.ownerId(), Status.DONE, job.total(),
                job.total(), job.message(), result, job.startedAt(), clock.instant()));
    }

    public void fail(UUID id, String message) {
        jobs.computeIfPresent(id, (key, job) -> new JobState(job.id(), job.ownerId(), Status.FAILED, job.total(),
                job.done(), message, null, job.startedAt(), clock.instant()));
    }

    /** The job, if it exists and has not expired. */
    public Optional<JobState> find(UUID id) {
        evictExpired();
        return Optional.ofNullable(jobs.get(id));
    }

    /** Number of jobs currently {@link Status#RUNNING} (metric {@code fs_async_jobs_running}). */
    public long running() {
        return jobs.values().stream().filter(job -> job.status() == Status.RUNNING).count();
    }

    private void runJob(UUID id, Work work) {
        Progress progress = new Progress() {
            @Override
            public void total(int total) {
                jobs.computeIfPresent(id, (key, job) -> new JobState(job.id(), job.ownerId(), job.status(), total,
                        job.done(), job.message(), job.result(), job.startedAt(), job.finishedAt()));
            }

            @Override
            public void done(int done) {
                progress(id, done);
            }

            @Override
            public void increment() {
                JobState job = jobs.get(id);
                if (job != null) {
                    progress(id, job.done() + 1);
                }
            }
        };
        try {
            complete(id, work.run(progress));
        } catch (Exception e) {
            log.warn("Job {} failed", id, e);
            fail(id, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /** Drops finished jobs whose result expired (also called by the cleanup job). */
    public void evictExpired() {
        Instant limit = clock.instant().minus(ttl);
        jobs.values().removeIf(job -> job.finishedAt() != null && job.finishedAt().isBefore(limit));
    }
}
