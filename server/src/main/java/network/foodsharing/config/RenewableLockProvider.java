package network.foodsharing.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

/**
 * Delegating ShedLock provider whose delegate can be recreated. ShedLock's JDBC provider remembers which lock rows it
 * has inserted and afterwards only updates them; when the dev seed reset drops and recreates the {@code shedlock} table
 * those updates hit no row and every job would silently stop running. {@link #renew()} starts with a fresh cache.
 *
 * <p>{@link #whilePaused} runs the seed reset with locked jobs held off: new lock requests are refused (the job run is
 * skipped like one whose lock another instance holds, no exception), and running jobs are waited for, so no job
 * writes into the database while it is dropped and restored.
 */
public class RenewableLockProvider implements LockProvider {

    private final Supplier<LockProvider> factory;
    private final AtomicReference<LockProvider> delegate = new AtomicReference<>();
    private final Object monitor = new Object();
    private boolean paused;
    private int active;

    public RenewableLockProvider(Supplier<LockProvider> factory) {
        this.factory = factory;
        this.delegate.set(factory.get());
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        synchronized (monitor) {
            if (paused) {
                return Optional.empty();
            }
            active++;
        }
        try {
            Optional<SimpleLock> lock = delegate.get().lock(lockConfiguration);
            if (lock.isEmpty()) {
                release();
            }
            return lock.map(TrackedLock::new);
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    /** Forgets all cached lock rows (call after the lock table was recreated). */
    public void renew() {
        delegate.set(factory.get());
    }

    /**
     * Refuses new locks, waits up to {@code maxWait} for held locks to be released, runs {@code work}, then renews
     * the row cache (the lock table was recreated) and accepts locks again.
     */
    public void whilePaused(Duration maxWait, Runnable work) {
        synchronized (monitor) {
            paused = true;
            long deadline = System.nanoTime() + maxWait.toNanos();
            try {
                while (active > 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        break;
                    }
                    monitor.wait(Math.max(1, remaining / 1_000_000));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            work.run();
        } finally {
            renew();
            synchronized (monitor) {
                paused = false;
            }
        }
    }

    /** Number of locks currently held through this provider. */
    public int activeLocks() {
        synchronized (monitor) {
            return active;
        }
    }

    private void release() {
        synchronized (monitor) {
            active--;
            monitor.notifyAll();
        }
    }

    /** Counts the lock as held until it is unlocked. */
    private final class TrackedLock implements SimpleLock {

        private final SimpleLock lock;
        private final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();

        private TrackedLock(SimpleLock lock) {
            this.lock = lock;
        }

        @Override
        public void unlock() {
            try {
                lock.unlock();
            } finally {
                releaseOnce();
            }
        }

        /** A successful extension replaces this lock (still counted once); a failed one loses it. */
        @Override
        public Optional<SimpleLock> extend(Duration lockAtMostFor, Duration lockAtLeastFor) {
            Optional<SimpleLock> extended = lock.extend(lockAtMostFor, lockAtLeastFor);
            if (extended.isEmpty()) {
                releaseOnce();
                return Optional.empty();
            }
            if (!done.compareAndSet(false, true)) {
                return extended;
            }
            return extended.map(TrackedLock::new);
        }

        private void releaseOnce() {
            if (done.compareAndSet(false, true)) {
                release();
            }
        }
    }
}
