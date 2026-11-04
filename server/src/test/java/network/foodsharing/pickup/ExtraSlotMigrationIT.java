package network.foodsharing.pickup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Changelog 187 (AU6, decision 164) on H2: {@code extra_slot_allowed} true becomes {@code max_extra_slots} 1, false
 * becomes 0, and a pickup may get several extra slots. 187-2 is run again on prepared data by restoring the old column
 * and removing its row from the changelog table. PostgreSQL runs the same changelog in LiquibasePostgresIT (CI).
 */
class ExtraSlotMigrationIT {

    private static final String MASTER = "classpath:db/changelog/db.changelog-master.yaml";
    private static final UUID S01 = UUID.fromString("00000020-0001-4000-8000-000000000001");
    private static final UUID S02 = UUID.fromString("00000020-0001-4000-8000-000000000002");

    @Test
    void switchBecomesACountAndSeveralExtraSlotsFit() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:extra-slot-187-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        run(dataSource, "schema,seed");

        jdbc.execute("alter table store add column extra_slot_allowed boolean default false not null");
        jdbc.update("update store set extra_slot_allowed = true where id = ?", S01);
        jdbc.execute("alter table store drop column max_extra_slots");
        assertThat(jdbc.update("delete from databasechangelog where id = '187-2'")).isEqualTo(1);

        run(dataSource, "schema");

        assertThat(max(jdbc, S01)).isEqualTo(1);
        assertThat(max(jdbc, S02)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where lower(table_name) = "
                + "'store' and lower(column_name) = 'extra_slot_allowed'", Integer.class)).isZero();
        // the unique constraint is gone: two extra slots on the same pickup
        for (int i = 0; i < 2; i++) {
            jdbc.update("insert into pickup_extra_slot (id, version, created_at, updated_at, store_id, region_id, "
                    + "pickup_at, user_id, previous_total_slots, new_total_slots) values (?, 0, current_timestamp, "
                    + "current_timestamp, ?, ?, timestamp with time zone '2026-11-26 18:00:00+00', ?, 2, 3)",
                    UUID.randomUUID(), S01, UUID.randomUUID(), UUID.randomUUID());
        }
        assertThat(jdbc.queryForObject("select count(*) from pickup_extra_slot where store_id = ? and pickup_at = "
                + "timestamp with time zone '2026-11-26 18:00:00+00'", Integer.class, S01)).isEqualTo(2);
    }

    private static int max(JdbcTemplate jdbc, UUID storeId) {
        return jdbc.queryForObject("select max_extra_slots from store where id = ?", Integer.class, storeId);
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
