package network.foodsharing.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * The application clock. Delegates to the system clock unless it is fixed ({@code app.clock.fixed} or the dev clock
 * endpoint). Always UTC; display time zones come from the region.
 */
public class MutableClock extends Clock {

    private final Clock base;
    private volatile Instant fixedAt;

    public MutableClock(Clock base, Instant fixedAt) {
        this.base = Objects.requireNonNull(base);
        this.fixedAt = fixedAt;
    }

    public void fixAt(Instant instant) {
        this.fixedAt = Objects.requireNonNull(instant);
    }

    public void release() {
        this.fixedAt = null;
    }

    public boolean isFixed() {
        return fixedAt != null;
    }

    /**
     * The underlying (normally system) time, ignoring a fixed instant. Only for values checked by third-party code
     * against the real time, e.g. the iat/exp claims of the fake OIDC provider's id tokens, which Spring Security
     * validates against the system clock.
     */
    public Instant realInstant() {
        return base.instant();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("The application clock is always UTC");
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedAt;
        return fixed != null ? fixed : base.instant();
    }
}
