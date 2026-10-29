package network.foodsharing.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.service.StoresCsvImportService;
import network.foodsharing.store.service.StoresCsvImportService.Row;
import network.foodsharing.store.service.StoresCsvImportService.Status;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.ZuerichData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** D16.2: the Zürich stores model file imports into zuerich-stadt on an empty production schema and is idempotent. */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class StoresCsvImportZuerichIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private RegionsCsvImportService regionsImport;

    @Autowired
    private StoresCsvImportService storesImport;

    @Autowired
    private StoreCategoryRepository categories;

    @Autowired
    private StoreChainRepository chains;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void loginAsOrga() {
        TestAuth.login(new SessionPrincipal(UUID.randomUUID(), UUID.randomUUID(), Role.ORGA, "orga@example.org"));
    }

    @AfterEach
    void logout() {
        TestAuth.clear();
    }

    @Test
    void zuerichStoresImportCompletelyAndRepeatUnchanged() throws Exception {
        ZuerichData.prepare(regionsImport, categories, chains);
        UUID city = jdbc.queryForObject("select id from region where slug = 'zuerich-stadt'", UUID.class);
        String csv = ZuerichData.read("stores.csv");

        var preview = storesImport.importCsv(city, csv, true);
        assertThat(preview.rows()).hasSize(40).allSatisfy(row -> {
            assertThat(row.errors()).as(row.name()).isEmpty();
            assertThat(row.status()).as(row.name()).isEqualTo(Status.CREATE);
        });
        assertThat(jdbc.queryForObject("select count(*) from store", Integer.class)).isZero();

        var live = storesImport.importCsv(city, csv, false);
        assertThat(live.created()).isEqualTo(40);
        assertThat(live.rows()).allSatisfy(row -> assertThat(row.warnings()).as(row.name())
                .doesNotContain("GEOCODING_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from store where region_id = ? and latitude is not null"
                + " and longitude is not null and deleted_at is null", Integer.class, city)).isEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from store where chain_id is not null", Integer.class))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'STORES_IMPORTED'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'STORE_CREATED'",
                Integer.class)).isEqualTo(40);

        var again = storesImport.importCsv(city, csv, false);
        assertThat(again.rows()).extracting(Row::status).containsOnly(Status.UNCHANGED);
        assertThat(again.skipped()).isEqualTo(40);
        assertThat(jdbc.queryForObject("select count(*) from store", Integer.class)).isEqualTo(40);
    }
}
