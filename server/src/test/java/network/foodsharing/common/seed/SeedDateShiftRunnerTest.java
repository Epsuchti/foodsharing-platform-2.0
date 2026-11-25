package network.foodsharing.common.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** A failing contributor rolls the shift back and keeps the anchor, but never stops the application start. */
class SeedDateShiftRunnerTest {

    @Test
    void failingContributorDoesNotStopTheStart() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:seed-shift-runner-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table seed_shift_state (id uuid primary key, version bigint, created_at timestamp, "
                + "updated_at timestamp, last_shift_anchor date)");
        SeedDateShiftContributor failing = delta -> {
            throw new IllegalStateException("duplicate key");
        };
        Clock clock = Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC);
        SeedDateShiftRunner runner = new SeedDateShiftRunner(jdbc, List.of(failing),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), clock);

        assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        assertThat(jdbc.queryForObject("select count(*) from seed_shift_state", Integer.class)).isZero();
    }
}
