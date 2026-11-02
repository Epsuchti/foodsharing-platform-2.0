package network.foodsharing.automation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Changelog 182 (decision 152) on H2: each gap list takes the largest own gap of its members (at least 1) as its group
 * gap; the members' own gap and switch stay as they are, and a group without members is not touched. The changeset is
 * run again on prepared data by removing its row from the changelog table. PostgreSQL runs the same SQL in
 * LiquibasePostgresIT (CI).
 */
class GapGroupMigrationIT {

    private static final String MASTER = "classpath:db/changelog/db.changelog-master.yaml";
    private static final String S01_POLICY = "00000022-0001-4000-8000-000000000001";
    private static final String S02_POLICY = "00000022-0001-4000-8000-000000000002";
    private static final String S03_POLICY = "00000022-0001-4000-8000-000000000004";
    private static final String SEED_GROUP = "00000022-0016-4000-8000-000000000001";

    @Test
    void groupGapIsTheMembersGapAfter182() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:gap-group-182-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        run(dataSource, "schema,seed");

        UUID zeroGroup = UUID.randomUUID();
        UUID emptyGroup = UUID.randomUUID();
        jdbc.update("update pickup_gap_group set minimum_gap_days = 9 where id = ?", UUID.fromString(SEED_GROUP));
        insertGroup(jdbc, zeroGroup, 9);
        insertGroup(jdbc, emptyGroup, 7);
        jdbc.update("update pickup_policy set minimum_gap_days = 4, gap_rule_enabled = true where id = ?",
                UUID.fromString(S01_POLICY));
        jdbc.update("update pickup_policy set minimum_gap_days = 3, gap_rule_enabled = false where id = ?",
                UUID.fromString(S03_POLICY));
        jdbc.update("update pickup_policy set minimum_gap_days = 0, gap_rule_enabled = false, gap_group_id = ? "
                + "where id = ?", zeroGroup, UUID.fromString(S02_POLICY));
        assertThat(jdbc.update("delete from databasechangelog where id = '182-1'")).isEqualTo(1);

        run(dataSource, "schema");

        assertThat(gap(jdbc, "pickup_gap_group", SEED_GROUP)).isEqualTo(4);
        assertThat(gap(jdbc, "pickup_gap_group", zeroGroup.toString())).isEqualTo(1);
        assertThat(gap(jdbc, "pickup_gap_group", emptyGroup.toString())).isEqualTo(7);
        // members untouched
        assertThat(gap(jdbc, "pickup_policy", S01_POLICY)).isEqualTo(4);
        assertThat(gap(jdbc, "pickup_policy", S03_POLICY)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select gap_rule_enabled from pickup_policy where id = ?", Boolean.class,
                UUID.fromString(S03_POLICY))).isFalse();
        assertThat(gap(jdbc, "pickup_policy", S02_POLICY)).isZero();
    }

    private static void insertGroup(JdbcTemplate jdbc, UUID id, int gap) {
        jdbc.update("insert into pickup_gap_group (id, version, created_at, updated_at, minimum_gap_days) "
                + "values (?, 0, current_timestamp, current_timestamp, ?)", id, gap);
    }

    private static int gap(JdbcTemplate jdbc, String table, String id) {
        return jdbc.queryForObject("select minimum_gap_days from " + table + " where id = ?", Integer.class,
                UUID.fromString(id));
    }

    private static void run(DriverManagerDataSource dataSource, String contexts) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(MASTER);
        liquibase.setContexts(contexts);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        liquibase.afterPropertiesSet();
    }
}
