package network.foodsharing.persona.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.config.PersonaPanelCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * The persona panel's password check (decision 173): constant-time comparison of SHA-256 digests, and per client IP
 * at most {@code max-failures-per-ip} wrong passwords within {@code failure-window}; after that every attempt from
 * that IP answers 429 with {@code Retry-After} until the oldest failure leaves the window. Time from the injected
 * {@link Clock}.
 */
@Component
@Profile("staging")
@Conditional(PersonaPanelCondition.class)
public class PersonaPanelGate {

    private static final Logger log = LoggerFactory.getLogger(PersonaPanelGate.class);

    private final byte[] expectedDigest;
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public PersonaPanelGate(AppProperties properties, Clock clock) {
        AppProperties.Staging.PersonaPanel panel = properties.getStaging().getPersonaPanel();
        this.expectedDigest = sha256(panel.getPassword());
        this.maxFailures = Math.max(1, panel.getMaxFailuresPerIp());
        this.window = panel.getFailureWindow();
        this.clock = clock;
    }

    /**
     * @throws ApiException 429 TOO_MANY_REQUESTS (with Retry-After) while the IP is blocked, 401
     *                      PERSONA_PANEL_PASSWORD_INVALID for a wrong or missing password (counted as a failure)
     */
    public void verify(String password, String clientIp) {
        Instant now = clock.instant();
        String key = clientIp == null ? "" : clientIp;
        Deque<Instant> failures = failuresByIp.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (failures) {
            Instant windowStart = now.minus(window);
            while (!failures.isEmpty() && !failures.peekFirst().isAfter(windowStart)) {
                failures.pollFirst();
            }
            if (failures.size() >= maxFailures) {
                Duration retryAfter = Duration.between(now, failures.peekFirst().plus(window));
                throw ApiException.tooManyRequests(ApiErrorCode.TOO_MANY_REQUESTS,
                        retryAfter.isNegative() || retryAfter.isZero() ? Duration.ofSeconds(1) : retryAfter);
            }
            if (password == null || !MessageDigest.isEqual(expectedDigest, sha256(password))) {
                failures.addLast(now);
                log.warn("Wrong persona panel password ({} of {} within {})", failures.size(), maxFailures, window);
                throw new ApiException(HttpStatus.UNAUTHORIZED, ApiErrorCode.PERSONA_PANEL_PASSWORD_INVALID);
            }
        }
        if (failuresByIp.size() > 10_000) {
            failuresByIp.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
