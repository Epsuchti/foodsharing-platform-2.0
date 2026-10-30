package network.foodsharing.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

/** The seed reset holds locked jobs off: no new locks while paused, held locks are waited for. */
class RenewableLockProviderTest {

    private static final LockConfiguration JOB = new LockConfiguration(Instant.parse("2026-11-02T09:00:00Z"),
            "some-job", Duration.ofMinutes(1), Duration.ZERO);

    private final AtomicInteger delegateCalls = new AtomicInteger();
    private final AtomicInteger providers = new AtomicInteger();
    private final RenewableLockProvider provider = new RenewableLockProvider(() -> {
        providers.incrementAndGet();
        return configuration -> {
            delegateCalls.incrementAndGet();
            return Optional.of(() -> { });
        };
    });

    @Test
    void locksAreRefusedWithoutExceptionWhilePausedAndTheCacheIsRenewedAfterwards() {
        boolean[] lockedDuringPause = {true};
        provider.whilePaused(Duration.ofSeconds(1), () -> lockedDuringPause[0] = provider.lock(JOB).isPresent());

        assertThat(lockedDuringPause[0]).isFalse();
        assertThat(delegateCalls).hasValue(0);
        assertThat(providers).hasValue(2);
        assertThat(provider.lock(JOB)).isPresent();
    }

    @Test
    void pauseWaitsForAHeldLockToBeReleased() throws Exception {
        SimpleLock held = provider.lock(JOB).orElseThrow();
        assertThat(provider.activeLocks()).isEqualTo(1);
        CompletableFuture<Integer> workSaw = new CompletableFuture<>();
        Thread reset = new Thread(() -> provider.whilePaused(Duration.ofSeconds(30),
                () -> workSaw.complete(provider.activeLocks())));
        reset.start();

        Thread.sleep(200);
        assertThat(workSaw).isNotDone();
        held.unlock();

        assertThat(workSaw.get(10, TimeUnit.SECONDS)).isZero();
        reset.join(10_000);
        held.unlock(); // a second unlock does not count twice
        assertThat(provider.activeLocks()).isZero();
    }

    @Test
    void pauseGoesAheadAfterTheMaximumWait() {
        provider.lock(JOB).orElseThrow();
        boolean[] ran = {false};

        provider.whilePaused(Duration.ofMillis(50), () -> ran[0] = true);

        assertThat(ran[0]).isTrue();
        assertThat(provider.lock(JOB)).isPresent();
    }
}
