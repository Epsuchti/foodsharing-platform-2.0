package network.foodsharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The full changelog, including the seed context, on PostgreSQL 17; Hibernate's schema validation must pass against
 * it (the context only starts when it does).
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema,seed", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class LiquibasePostgresIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void schemaAndSeedLoadOnPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 17");
        assertThat(jdbc.queryForObject("select count(*) from user_account", Integer.class))
                .isEqualTo(SeedUsers.values().length);
        Map<String, Object> newbie = jdbc.queryForMap(
                "select email, quiz_role, verified, verified_at, registered_at from user_account where id = ?",
                SeedUsers.NEWBIE.id());
        assertThat(newbie.get("email")).isEqualTo("newbie@local");
        assertThat(newbie.get("quiz_role")).isNull();
        assertThat(newbie.get("verified")).isEqualTo(false);
        assertThat(newbie.get("verified_at")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from history_entry where field is null and action = 'LOGIN'",
                Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select metadata from history_entry where action = 'ACCOUNT_CREATED' "
                + "and subject_id = ?", String.class, SeedUsers.ORGA.id())).isEqualTo("{\"language\":\"DE\"}");
        assertThat(jdbc.queryForObject("select max(occurred_at) <= timestamptz '2026-11-02 09:00:00+00' from history_entry",
                Boolean.class)).isTrue();
        // The foodsaver number sequence starts above the seed numbers.
        assertThat(jdbc.queryForObject("select nextval('foodsaver_number_seq')", Long.class)).isGreaterThan(100014L);

        List<String> tables = jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_schema = 'public'", String.class);
        if (tables.contains("auth_identity")) {
            assertThat(jdbc.queryForObject("select count(*) from auth_identity", Integer.class)).isEqualTo(16);
        }
        if (tables.contains("email_blocklist")) {
            assertThat(jdbc.queryForObject("select count(*) from email_blocklist", Integer.class)).isEqualTo(2);
        }
    }
}
