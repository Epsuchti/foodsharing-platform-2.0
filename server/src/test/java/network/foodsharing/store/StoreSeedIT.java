package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.store.domain.CooperationStatus;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.TeamSearchStatus;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** The store seed (docs/seed-ids.md, B2): 25 stores, every enum value, Zürich coordinates, the team constellations. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-seed-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class StoreSeedIT {

    static final String SEED_STORES = " from store where cast(id as varchar(36)) like '00000020-0001-%'";

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void storesCoverEveryStatusInsideZurich() {
        assertThat(count("select count(*)" + SEED_STORES)).isEqualTo(25);
        for (CooperationStatus status : CooperationStatus.values()) {
            assertThat(count("select count(*)" + SEED_STORES + " and cooperation_status = ?", status.name()))
                    .as(status.name()).isPositive();
        }
        assertThat(count("select count(*)" + SEED_STORES + " and cooperation_status = 'COOPERATION_ESTABLISHED'"))
                .isEqualTo(18);
        for (TeamSearchStatus status : TeamSearchStatus.values()) {
            assertThat(count("select count(*)" + SEED_STORES + " and team_status = ?", status.name()))
                    .as(status.name()).isPositive();
        }
        assertThat(count("select count(*)" + SEED_STORES + " and (latitude < 47.3 or latitude > 47.6 "
                + "or longitude < 8.4 or longitude > 8.8)")).isZero();
        assertThat(count("select count(*)" + SEED_STORES + " and deleted_at is not null")).isZero();
        assertThat(count("select count(*)" + SEED_STORES + " and (prefetch_days < 1 or prefetch_days > 42)")).isZero();
    }

    @Test
    void metadataTables() {
        assertThat(count("select count(*) from store_category")).isGreaterThanOrEqualTo(8);
        assertThat(count("select count(*) from grocery")).isGreaterThanOrEqualTo(8);
        assertThat(count("select count(*) from pickup_weight_category")).isEqualTo(9);
        assertThat(count("select count(*) from store_chain")).isGreaterThanOrEqualTo(6);
        assertThat(count("select count(*) from store_chain_kam where user_id = ?", SeedUsers.BIEB.id())).isPositive();
    }

    @Test
    void teamConstellations() {
        for (StoreMembershipStatus status : StoreMembershipStatus.values()) {
            assertThat(count("select count(*) from store_membership where status = ?", status.name()))
                    .as(status.name()).isPositive();
        }
        assertThat(count("select count(*) from store_membership where user_id = ? and manager = true",
                SeedUsers.BIEB.id())).isGreaterThanOrEqualTo(6);
        assertThat(managers("10")).isEqualTo(2);
        assertThat(managers("11")).isEqualTo(3);
        // every store with a team has at least one manager
        assertThat(count("select count(distinct store_id) from store_membership m where not exists (select 1 from "
                + "store_membership x where x.store_id = m.store_id and x.manager = true)")).isZero();
        assertThat(count("select count(*) from store_membership where user_id = ? and status = 'JUMPER'",
                SeedUsers.JUMPER.id())).isEqualTo(2);
        assertThat(count("select count(*) from store_membership where user_id = ?", SeedUsers.ORGA.id())).isZero();
    }

    private int managers(String store) {
        return count("select count(*) from store_membership where manager = true and store_id = ?",
                java.util.UUID.fromString("00000020-0001-4000-8000-0000000000" + store));
    }
}
