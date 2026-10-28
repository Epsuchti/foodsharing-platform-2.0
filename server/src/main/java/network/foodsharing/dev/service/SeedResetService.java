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
 * Puts the database back into the seed state: {@code DROP ALL OBJECTS} and a fresh Liquibase run with the configured
 * contexts (schema + seed in local/e2e). H2 only; refuses on any other database.
 */
@Service
@Profile({"local", "e2e"})
public class SeedResetService {

    private static final Logger log = LoggerFactory.getLogger(SeedResetService.class);

    private final DataSource dataSource;
    private final SpringLiquibase liquibase;
    private final network.foodsharing.config.RenewableLockProvider lockProvider;

    public SeedResetService(DataSource dataSource, SpringLiquibase liquibase,
                            network.foodsharing.config.RenewableLockProvider lockProvider) {
        this.lockProvider = lockProvider;
        this.dataSource = dataSource;
        this.liquibase = liquibase;
    }

    /**
     * Drops everything and re-runs the changelog. Not transactional on purpose: DDL commits immediately and
     * Liquibase manages its own connections. Serialised so parallel resets cannot interleave.
     *
     * @throws ApiException 409 NOT_IMPLEMENTED when the database is not H2
     */
    public synchronized void reset() {
        if (!isH2()) {
            throw new ApiException(HttpStatus.CONFLICT, ApiErrorCode.NOT_IMPLEMENTED,
                    "Seed reset is only supported on H2 databases");
        }
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP ALL OBJECTS");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not drop the H2 database", e);
        }
        try {
            liquibase.afterPropertiesSet();
            lockProvider.renew(); // the shedlock table was recreated: forget cached lock rows
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Liquibase failed while restoring the seed state", e);
        }
        log.info("Seed reset done in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    public boolean isH2() {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the database type", e);
        }
    }
}
