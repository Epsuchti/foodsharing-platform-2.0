package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.integration.spring.SpringLiquibase;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Decision 173: the test environment's database first ran prod's contexts ({@code schema}); switching it to
 * {@code prod,staging} runs {@code schema,seed} on the same PostgreSQL 17 database. The seed loads on top, the
 * foodsaver number sequence is untouched (seed numbers are fixed, 100001–100099) and the staging start shifts the
 * seed dates on PostgreSQL ({@code test} stands in for {@code prod} as the base profile, StagingProfileGuard).
 */
@SpringBootTest(properties = {"app.scheduling.enabled=false", "app.clock.fixed=2026-11-09T09:00:00Z"})
@ActiveProfiles({"test", "staging"})
@Testcontainers
class StagingSeedOnExistingSchemaPostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    private static long sequenceBeforeSeed;

    @Autowired
    private JdbcTemplate jdbc;

    /** Runs before the Spring context: the database as prod left it (schema only, no seed rows). */
    @BeforeAll
    static void schemaOnlyFirst() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.setContexts("schema");
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class)).isZero();
        sequenceBeforeSeed = jdbc.queryForObject("select last_value from foodsaver_number_seq", Long.class);
    }

    @Test
    void seedLoadsOnTopAndTheSequenceIsUnaffected() {
        assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class))
                .isEqualTo(SeedUsers.values().length);
        assertThat(jdbc.queryForObject("select email from user_account where id = ?", String.class,
                SeedUsers.ORGA.id())).isEqualTo("orga@local");
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog where contexts like '%seed%'",
                Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select last_value from foodsaver_number_seq", Long.class))
                .isEqualTo(sequenceBeforeSeed);
        assertThat(jdbc.queryForObject("select max(foodsaver_number) from user_account", Long.class))
                .isLessThan(sequenceBeforeSeed);
    }

    @Test
    void stagingStartShiftsTheSeedDatesOnPostgres() {
        // SeedDateShiftRunner (local + staging) moved the seed by the 7 days between SEED_NOW and the fixed clock
        assertThat(jdbc.queryForObject("select cast(last_shift_anchor as varchar(10)) from seed_shift_state",
                String.class)).isEqualTo("2026-11-09");
    }
}
