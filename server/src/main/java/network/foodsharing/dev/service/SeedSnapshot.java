package network.foodsharing.dev.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * An H2 {@code SCRIPT} of the database exactly as the Liquibase changelog leaves it (schema, seed rows, sequences,
 * changelog tables), so the dev seed reset can restore it with {@code RUNSCRIPT} in about a second instead of
 * re-running the whole changelog. Taken at startup when Liquibase built the database from scratch (the in-memory
 * {@code e2e} database), otherwise by the first reset after its changelog run (a {@code local} file database that
 * already held data). The file lives in the temp directory, one per application context, and is deleted on exit.
 */
@Component
@Profile({"local", "e2e"})
public class SeedSnapshot implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SeedSnapshot.class);

    private volatile Path file;

    private volatile boolean emptyBeforeChangelog;

    /** Notes whether the changelog is about to run on an empty H2 database. */
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase && liquibase.getDataSource() != null) {
            emptyBeforeChangelog = isH2(liquibase.getDataSource()) && !hasChangelog(liquibase.getDataSource());
        }
        return bean;
    }

    /** The changelog has run (in {@code afterPropertiesSet}): snapshot a database it built from scratch. */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase && emptyBeforeChangelog) {
            emptyBeforeChangelog = false;
            capture(liquibase.getDataSource());
        }
        return bean;
    }

    public boolean available() {
        return file != null;
    }

    /** Writes the script of the current database (call right after a changelog run on an empty database). */
    public void capture(DataSource dataSource) {
        long started = System.nanoTime();
        try {
            Path target = Files.createTempFile("foodsharing-seed-", ".sql");
            target.toFile().deleteOnExit();
            execute(dataSource, "SCRIPT NOPASSWORDS TO '" + sqlPath(target) + "'");
            Path previous = file;
            file = target;
            if (previous != null) {
                Files.deleteIfExists(previous);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the seed snapshot", e);
        }
        log.info("Seed snapshot written in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    /** Replays the script into an emptied database ({@code DROP ALL OBJECTS} first). */
    public void restore(DataSource dataSource) {
        execute(dataSource, "RUNSCRIPT FROM '" + sqlPath(file) + "'");
    }

    private static String sqlPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "''");
    }

    private static void execute(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Seed snapshot statement failed: " + sql, e);
        }
    }

    private static boolean isH2(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            return false;
        }
    }

    private static boolean hasChangelog(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (tables.next()) {
                if ("DATABASECHANGELOG".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return true; // unknown: let the first reset take the snapshot
        }
    }
}
