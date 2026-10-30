package network.foodsharing.dev.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Puts the database back into the seed state: {@code DROP ALL OBJECTS}, then the {@link SeedSnapshot} of a fresh
 * changelog run is replayed (a fresh Liquibase run with the configured contexts, schema + seed in local/e2e, when there
 * is no snapshot yet; that run then takes it). H2 only; refuses on any other database.
 */
@Service
@Profile({"local", "e2e"})
public class SeedResetService {

    private static final Logger log = LoggerFactory.getLogger(SeedResetService.class);

    /** How long a reset waits for running jobs to release their locks before it goes ahead. */
    private static final java.time.Duration JOB_WAIT = java.time.Duration.ofSeconds(5);

    private final DataSource dataSource;
    private final SpringLiquibase liquibase;
    private final network.foodsharing.config.RenewableLockProvider lockProvider;
    private final SeedSnapshot snapshot;

    public SeedResetService(DataSource dataSource, SpringLiquibase liquibase,
                            network.foodsharing.config.RenewableLockProvider lockProvider, SeedSnapshot snapshot) {
        this.lockProvider = lockProvider;
        this.dataSource = dataSource;
        this.liquibase = liquibase;
        this.snapshot = snapshot;
    }

    /**
     * Drops everything and restores the seed state while locked jobs are held off
     * ({@link network.foodsharing.config.RenewableLockProvider#whilePaused}: no job writes into the half-restored
     * database or hits the missing {@code shedlock} table; the lock row cache is renewed afterwards). Not
     * transactional on purpose: DDL commits immediately and Liquibase manages its own connections. Serialised so
     * parallel resets cannot interleave.
     *
     * @throws ApiException 409 NOT_IMPLEMENTED when the database is not H2
     */
    public synchronized void reset() {
        if (!isH2()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.NOT_IMPLEMENTED,
                    "Seed reset is only supported on H2 databases");
        }
        long started = System.nanoTime();
        long[] phases = new long[2];
        String[] source = new String[1];
        lockProvider.whilePaused(JOB_WAIT, () -> {
            phases[0] = System.nanoTime();
            dropAll();
            phases[1] = System.nanoTime();
            source[0] = restore();
        });
        long done = System.nanoTime();
        log.info("Seed reset done in {} ms (waiting for jobs {} ms, drop {} ms, {} {} ms)",
                (done - started) / 1_000_000, (phases[0] - started) / 1_000_000,
                (phases[1] - phases[0]) / 1_000_000, source[0], (done - phases[1]) / 1_000_000);
    }

    /** @return where the seed state came from ({@code snapshot} or {@code changelog}) */
    private String restore() {
        if (snapshot.available()) {
            try {
                snapshot.restore(dataSource);
                return "snapshot";
            } catch (IllegalStateException e) { // e.g. the temp file was cleaned up: rebuild from the changelog
                log.warn("Seed snapshot could not be restored, running the changelog: {}", e.getMessage());
                dropAll();
            }
        }
        runChangelog();
        try {
            snapshot.capture(dataSource);
        } catch (RuntimeException e) { // the next reset runs the changelog again
            log.warn("Seed snapshot could not be written: {}", e.getMessage());
        }
        return "changelog";
    }

    private void dropAll() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not drop the H2 database", e);
        }
    }

    private void runChangelog() {
        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Liquibase failed while restoring the seed state", e);
        }
    }

    public boolean isH2() {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the database type", e);
        }
    }
}
