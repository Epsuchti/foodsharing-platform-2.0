package network.foodsharing.common.integration;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * One {@link CircuitBreaker} per {@link Integration} (A26). Clients call through {@link #call}; while a breaker is
 * open the call fails fast with 503 {@code INTEGRATION_UNAVAILABLE}. When a breaker opens, an
 * {@link IntegrationDownEvent} is published (ORGA bell, M4 B3). Clients register their mode and a cheap probe.
 */
@Component
public class IntegrationRegistry implements network.foodsharing.common.seed.InMemoryState {

    /** live = real calls, log = captured only (local/e2e/test). */
    public enum Mode { LIVE, LOG }

    /** Published once per outage when a breaker opens. */
    public record IntegrationDownEvent(Integration integration, String error) {
    }

    private final Map<Integration, CircuitBreaker> breakers = new EnumMap<>(Integration.class);
    private final Map<Integration, Mode> modes = new EnumMap<>(Integration.class);
    private final ApplicationEventPublisher events;

    public IntegrationRegistry(AppProperties properties, Clock clock, ApplicationEventPublisher events) {
        this.events = events;
        Duration probeInterval = Duration.parse(properties.getIntegrations().getProbeInterval());
        Duration history = Duration.ofDays(properties.getIntegrations().getOutageHistoryDays());
        for (Integration integration : Integration.values()) {
            breakers.put(integration, new CircuitBreaker(integration, clock, probeInterval, history));
            modes.put(integration, Mode.LIVE);
        }
        modes.put(Integration.SMTP, mode(!"capture".equals(properties.getMail().getTransport())));
        modes.put(Integration.WEB_PUSH, mode("webpush".equals(properties.getPush().getMode())));
        modes.put(Integration.TELEGRAM, mode("live".equals(properties.getTelegram().getMode())));
        modes.put(Integration.GEOCODING, mode(!"static".equals(properties.getGeocoding().getProvider())));
        modes.put(Integration.HOLIDAY_CALENDAR, mode(!"static".equals(properties.getAutomation().getHolidayProvider())));
        modes.put(Integration.WALLET_APPLE, mode(properties.getWallet().getApple().isEnabled()));
        modes.put(Integration.WALLET_GOOGLE, mode(properties.getWallet().getGoogle().isEnabled()));
        modes.put(Integration.IMAP, Mode.LOG); // milestone 5
        for (Map.Entry<Integration, Mode> entry : modes.entrySet()) {
            // Log-only integrations are always "reachable"; wallets sign locally (no remote call to probe).
            if (entry.getValue() == Mode.LOG || entry.getKey() == Integration.WALLET_APPLE
                    || entry.getKey() == Integration.WALLET_GOOGLE) {
                breakers.get(entry.getKey()).setProbe(() -> true);
            }
        }
    }

    private static Mode mode(boolean live) {
        return live ? Mode.LIVE : Mode.LOG;
    }

    /** Called by a live client on start-up: its cheap reachability probe. */
    public void registerProbe(Integration integration, BooleanSupplier probe) {
        breakers.get(integration).setProbe(probe);
    }

    public synchronized Mode mode(Integration integration) {
        return modes.get(integration);
    }

    public CircuitBreaker breaker(Integration integration) {
        return breakers.get(integration);
    }

    public boolean shouldSkip(Integration integration) {
        return breakers.get(integration).shouldSkip();
    }

    /**
     * Runs {@code call} through the breaker: fails fast while open, records success, and on a failure reports it
     * (which may open the breaker) and rethrows the original exception.
     */
    public <T> T call(Integration integration, String where, Supplier<T> call) {
        CircuitBreaker breaker = breakers.get(integration);
        if (breaker.shouldSkip()) {
            throw unavailable(integration);
        }
        try {
            T result = call.get();
            breaker.markSucceeded();
            return result;
        } catch (RuntimeException ex) {
            CircuitState before = breaker.state();
            if (breaker.reportFailure(where, ex) && before == CircuitState.CLOSED) {
                events.publishEvent(new IntegrationDownEvent(integration, breaker.snapshot().lastError()));
            }
            throw ex;
        }
    }

    public void run(Integration integration, String where, Runnable call) {
        call(integration, where, () -> {
            call.run();
            return null;
        });
    }

    /** Dev only: opens the breaker as if an outage had been detected and publishes {@link IntegrationDownEvent}. */
    public void simulateOutage(Integration integration) {
        CircuitBreaker breaker = breakers.get(integration);
        if (breaker.forceOpen("dev", "simulated outage")) {
            events.publishEvent(new IntegrationDownEvent(integration, breaker.snapshot().lastError()));
        }
    }

    /** Dev seed reset: every breaker closed, no outage history. */
    @Override
    public void resetInMemoryState() {
        breakers.values().forEach(CircuitBreaker::reset);
    }

    public List<CircuitBreaker.Snapshot> snapshots() {
        return breakers.values().stream().map(CircuitBreaker::snapshot).toList();
    }

    public static ApiException unavailable(Integration integration) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.INTEGRATION_UNAVAILABLE)
                .withArg("integration", integration.name())
                .withRetryAfter(Duration.ofMinutes(5));
    }
}
