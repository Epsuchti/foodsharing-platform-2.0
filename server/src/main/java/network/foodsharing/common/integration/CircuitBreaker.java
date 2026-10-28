package network.foodsharing.common.integration;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Availability of one external integration (A26), a port of the reference {@code FoodsharingAvailability}: a failure
 * opens the breaker only when it looks like an outage <em>and</em> a probe confirms it; while open, calls are skipped
 * until {@code nextProbeAt}, then one probe decides (closes, or stays open for another interval). Finished outages
 * are kept for {@code outageHistory} so deadlines can be extended by the time the integration was down.
 */
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);
    private static final int MAX_ERROR = 500;

    private record Outage(Instant from, Instant to) {
    }

    private final Integration integration;
    private final Clock clock;
    private final Duration probeInterval;
    private final Duration outageHistory;
    private BooleanSupplier probe = () -> false;

    private Instant lastOkAt;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private Instant downSince;
    private Instant nextProbeAt;
    private int failureCount;
    private String lastError;
    private final List<Outage> finishedOutages = new ArrayList<>();

    public CircuitBreaker(Integration integration, Clock clock, Duration probeInterval, Duration outageHistory) {
        this.integration = integration;
        this.clock = clock;
        this.probeInterval = probeInterval;
        this.outageHistory = outageHistory;
        this.lastOkAt = clock.instant();
    }

    /** Cheap reachability check of the integration (true = reachable); default: unknown = not reachable. */
    public synchronized void setProbe(BooleanSupplier probe) {
        this.probe = probe;
    }

    /** Connect/read timeouts, unknown hosts, refused connections and 5xx answers count as outage. */
    public static boolean looksLikeOutage(Throwable throwable) {
        for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ResourceAccessException || t instanceof InterruptedIOException
                    || t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException || t.getClass().getSimpleName().equals("MailConnectException")) {
                return true;
            }
            if (t instanceof RestClientResponseException response && response.getStatusCode().is5xxServerError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reports a failed call. Returns true when the breaker is (now) open, i.e. callers should stop for this run.
     * The breaker opens only when the failure looks like an outage and the probe fails too.
     */
    public synchronized boolean reportFailure(String where, Throwable failure) {
        Instant now = clock.instant();
        failureCount++;
        lastFailureAt = now;
        lastError = summarize(where, failure);
        if (downSince != null) {
            return true;
        }
        if (!looksLikeOutage(failure) || probeSafely()) {
            return false;
        }
        downSince = lastOkAt.isBefore(now) ? lastOkAt : now;
        nextProbeAt = now.plus(probeInterval);
        log.warn("{} is down ({}); skipping calls, next probe in {}.", integration, lastError, probeInterval);
        return true;
    }

    /** True while the breaker is open and the next probe is not due (or the due probe failed). */
    public synchronized boolean shouldSkip() {
        if (downSince == null) {
            return false;
        }
        Instant now = clock.instant();
        if (now.isBefore(nextProbeAt)) {
            return true;
        }
        if (probeSafely()) {
            markUp(now);
            return false;
        }
        nextProbeAt = now.plus(probeInterval);
        log.warn("{} is still down since {}; next probe in {}.", integration, downSince, probeInterval);
        return true;
    }

    /** Forces a probe now (admin "probe"): closes on success, opens (or stays open) on failure. */
    public synchronized boolean probeNow() {
        Instant now = clock.instant();
        if (probeSafely()) {
            if (downSince != null) {
                markUp(now);
            }
            lastOkAt = now;
            return true;
        }
        if (downSince == null) {
            downSince = now;
        }
        nextProbeAt = now.plus(probeInterval);
        return false;
    }

    /** Records a successful call; narrows down when the integration was last known to be up. */
    public synchronized void markSucceeded() {
        Instant now = clock.instant();
        lastSuccessAt = now;
        failureCount = 0;
        if (downSince != null) {
            markUp(now);
        }
        lastOkAt = now;
    }

    /** Time within {@code [from, to]} the integration was down, including a running outage. */
    public synchronized Duration outageOverlap(Instant from, Instant to) {
        Duration total = Duration.ZERO;
        List<Outage> all = new ArrayList<>(finishedOutages);
        if (downSince != null) {
            all.add(new Outage(downSince, clock.instant()));
        }
        for (Outage outage : all) {
            Instant start = outage.from().isAfter(from) ? outage.from() : from;
            Instant end = outage.to().isBefore(to) ? outage.to() : to;
            if (end.isAfter(start)) {
                total = total.plus(Duration.between(start, end));
            }
        }
        return total;
    }

    /** Moves {@code deadline} back by the time the integration was down between {@code start} and the deadline. */
    public Instant extendDeadline(Instant start, Instant deadline) {
        Instant extended = deadline;
        for (int i = 0; i < 5; i++) {
            Instant next = deadline.plus(outageOverlap(start, extended));
            if (next.equals(extended)) {
                break;
            }
            extended = next;
        }
        return extended;
    }

    public synchronized CircuitState state() {
        if (downSince == null) {
            return CircuitState.CLOSED;
        }
        return clock.instant().isBefore(nextProbeAt) ? CircuitState.OPEN : CircuitState.HALF_OPEN;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(integration, state(), lastSuccessAt, lastFailureAt, downSince,
                downSince == null ? null : nextProbeAt, failureCount, lastError);
    }

    /** Read-only view for the admin page. */
    public record Snapshot(Integration integration, CircuitState state, Instant lastSuccessAt, Instant lastFailureAt,
            Instant downSince, Instant nextProbeAt, int failureCount, String lastError) {
    }

    private void markUp(Instant now) {
        log.info("{} is back after an outage since {}.", integration, downSince);
        finishedOutages.add(new Outage(downSince, now));
        finishedOutages.removeIf(outage -> outage.to().isBefore(now.minus(outageHistory)));
        downSince = null;
        nextProbeAt = null;
        lastOkAt = now;
    }

    private boolean probeSafely() {
        try {
            return probe.getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String summarize(String where, Throwable failure) {
        String text = where + ": " + failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : " " + failure.getMessage());
        return text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
    }
}
