package network.foodsharing.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import network.foodsharing.common.integration.CircuitState;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.common.realtime.RealtimeSessionRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Operations metrics of the platform (D19.3, {@code docs/monitoring.md}); names are the Micrometer form of the
 * Prometheus names used by {@code server/deploy/monitoring/alerts.yml} and the Grafana dashboard
 * ({@code MetricsContractTest}). Gauges read their state lazily on scrape (database values cached 15 s); events
 * (job runs, delivery failures, rate-limit rejections, login failures) are recorded through the static methods,
 * which go through Micrometer's global registry so low-level helpers need no extra dependency.
 */
@Component
public class OperationsMetrics {

    public static final String OUTBOX_PENDING = "fs.outbox.pending";
    public static final String OUTBOX_FAILED = "fs.outbox.failed";
    public static final String OUTBOX_OLDEST_DUE_AGE = "fs.outbox.oldest.pending.age";
    public static final String DELIVERY_FAILURES = "fs.notification.delivery.failures";
    public static final String BREAKER_STATE = "fs.circuit.breaker.state";
    public static final String JOB_DURATION = "fs.job.duration";
    public static final String JOB_LAST_SUCCESS = "fs.job.last.success.timestamp";
    public static final String JOB_CONSECUTIVE_FAILURES = "fs.job.consecutive.failures";
    public static final String ASYNC_JOBS_RUNNING = "fs.async.jobs.running";
    public static final String WEBSOCKET_USERS = "fs.websocket.connected.users";
    public static final String RATE_LIMIT_REJECTIONS = "fs.rate.limit.rejections";
    public static final String LOGIN_FAILURES = "fs.login.failures";

    public static final List<String> CHANNELS = List.of("mail", "push", "telegram");
    public static final List<String> LIMITERS = List.of("login-ip", "login-email", "basket-requests", "client-errors",
            "group-mails", "support", "messages");
    public static final List<String> LOGIN_FAILURE_REASONS = List.of("invalid-token", "expired-token", "passkey");

    private static final Duration DB_CACHE = Duration.ofSeconds(15);
    private static final Map<String, JobState> JOBS = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final JdbcClient jdbc;
    private final Clock clock;
    private volatile OutboxSnapshot outbox;

    private record OutboxSnapshot(Instant at, long pending, long failed, double oldestDueAgeSeconds) {
    }

    private record JobState(AtomicLong lastSuccessEpochSeconds, AtomicInteger consecutiveFailures) {
    }

    public OperationsMetrics(MeterRegistry registry, JdbcClient jdbc, Clock clock, IntegrationRegistry integrations,
                             ProgressTracker progress, RealtimeSessionRegistry realtime) {
        this.registry = registry;
        this.jdbc = jdbc;
        this.clock = clock;
        Gauge.builder(OUTBOX_PENDING, this, m -> m.outbox().pending())
                .description("Mails waiting in the outbox").register(registry);
        Gauge.builder(OUTBOX_FAILED, this, m -> m.outbox().failed())
                .description("Mails that reached the maximum number of attempts").register(registry);
        Gauge.builder(OUTBOX_OLDEST_DUE_AGE, this, m -> m.outbox().oldestDueAgeSeconds()).baseUnit("seconds")
                .description("How long the oldest due pending mail has been waiting").register(registry);
        for (Integration integration : Integration.values()) {
            Gauge.builder(BREAKER_STATE, integrations, r -> stateValue(r.breaker(integration).state()))
                    .tag("integration", integration.name())
                    .description("Circuit breaker state: 0 closed, 1 half-open, 2 open").register(registry);
        }
        Gauge.builder(ASYNC_JOBS_RUNNING, progress, ProgressTracker::running)
                .description("Background jobs (imports, exports) currently running").register(registry);
        Gauge.builder(WEBSOCKET_USERS, realtime, r -> r.connectedUsers().size())
                .description("Users with an open WebSocket (STOMP) session").register(registry);
        CHANNELS.forEach(channel -> deliveryCounter(registry, channel));
        LIMITERS.forEach(limiter -> rateLimitCounter(registry, limiter));
        LOGIN_FAILURE_REASONS.forEach(reason -> loginFailureCounter(registry, reason));
        JOBS.keySet().forEach(this::registerJobMeters);
    }

    /** Registers the per-job meters so every job is visible before its first run (called by {@code JobRunner}). */
    public static void registerJob(String jobName) {
        JOBS.computeIfAbsent(jobName, name -> new JobState(new AtomicLong(), new AtomicInteger()));
        for (MeterRegistry registry : Metrics.globalRegistry.getRegistries()) {
            registerJobMeters(registry, jobName);
        }
    }

    /** Records one job run. */
    public static void jobFinished(String jobName, Duration duration, boolean success, Instant finishedAt) {
        registerJob(jobName);
        JobState state = JOBS.get(jobName);
        if (success) {
            state.lastSuccessEpochSeconds().set(finishedAt.getEpochSecond());
            state.consecutiveFailures().set(0);
        } else {
            state.consecutiveFailures().incrementAndGet();
        }
        Timer.builder(JOB_DURATION).tag("name", jobName).tag("outcome", success ? "OK" : "FAILED")
                .register(Metrics.globalRegistry).record(duration);
    }

    /** A notification could not be delivered on {@code channel} ({@link #CHANNELS}). */
    public static void deliveryFailed(String channel) {
        deliveryCounter(Metrics.globalRegistry, channel).increment();
    }

    /** A rate limiter ({@link #LIMITERS}) rejected a request. */
    public static void rateLimited(String limiter) {
        rateLimitCounter(Metrics.globalRegistry, limiter).increment();
    }

    /** A login attempt failed ({@link #LOGIN_FAILURE_REASONS}). */
    public static void loginFailed(String reason) {
        loginFailureCounter(Metrics.globalRegistry, reason).increment();
    }

    static double stateValue(CircuitState state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }

    private void registerJobMeters(String jobName) {
        registerJobMeters(registry, jobName);
    }

    private static void registerJobMeters(MeterRegistry registry, String jobName) {
        JobState state = JOBS.get(jobName);
        if (state == null) {
            return;
        }
        Gauge.builder(JOB_LAST_SUCCESS, state.lastSuccessEpochSeconds(), AtomicLong::get).tag("name", jobName)
                .baseUnit("seconds").description("Epoch seconds of the last successful run (0 = none since start)")
                .register(registry);
        Gauge.builder(JOB_CONSECUTIVE_FAILURES, state.consecutiveFailures(), AtomicInteger::get).tag("name", jobName)
                .description("Failed runs in a row").register(registry);
        for (String outcome : List.of("OK", "FAILED")) {
            Timer.builder(JOB_DURATION).tag("name", jobName).tag("outcome", outcome).register(registry);
        }
    }

    private static Counter deliveryCounter(MeterRegistry registry, String channel) {
        return Counter.builder(DELIVERY_FAILURES).tag("channel", channel)
                .description("Failed notification deliveries").register(registry);
    }

    private static Counter rateLimitCounter(MeterRegistry registry, String limiter) {
        return Counter.builder(RATE_LIMIT_REJECTIONS).tag("limiter", limiter)
                .description("Requests rejected by a rate limiter").register(registry);
    }

    private static Counter loginFailureCounter(MeterRegistry registry, String reason) {
        return Counter.builder(LOGIN_FAILURES).tag("reason", reason)
                .description("Failed login attempts").register(registry);
    }

    private OutboxSnapshot outbox() {
        Instant now = clock.instant();
        OutboxSnapshot current = outbox;
        if (current != null && current.at().plus(DB_CACHE).isAfter(now)) {
            return current;
        }
        try {
            long pending = count("PENDING");
            long failed = count("FAILED");
            Timestamp oldest = jdbc.sql("select min(next_attempt_at) from mail_outbox where status = 'PENDING' "
                    + "and next_attempt_at <= :now").param("now", Timestamp.from(now))
                    .query(Timestamp.class).optional().orElse(null);
            double age = oldest == null ? 0 : Math.max(0, Duration.between(oldest.toInstant(), now).toSeconds());
            current = new OutboxSnapshot(now, pending, failed, age);
        } catch (RuntimeException ex) {
            current = current == null ? new OutboxSnapshot(now, 0, 0, 0) : current;
        }
        outbox = current;
        return current;
    }

    private long count(String status) {
        return jdbc.sql("select count(*) from mail_outbox where status = :status").param("status", status)
                .query(Long.class).single();
    }
}
