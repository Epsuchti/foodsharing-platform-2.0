package network.foodsharing.config;

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
 */
public class RenewableLockProvider implements LockProvider {

    private final Supplier<LockProvider> factory;
    private final AtomicReference<LockProvider> delegate = new AtomicReference<>();

    public RenewableLockProvider(Supplier<LockProvider> factory) {
        this.factory = factory;
        this.delegate.set(factory.get());
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return delegate.get().lock(lockConfiguration);
    }

    /** Forgets all cached lock rows (call after the lock table was recreated). */
    public void renew() {
        delegate.set(factory.get());
    }
}
