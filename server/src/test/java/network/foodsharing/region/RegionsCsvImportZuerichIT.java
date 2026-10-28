package network.foodsharing.region;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.region.service.RegionsCsvImportService.Row;
import network.foodsharing.region.service.RegionsCsvImportService.Status;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.testsupport.TestAuth;
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

/** D16.1: the Zürich regions file imports into an empty production schema (PostgreSQL, no seed) and is idempotent. */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class RegionsCsvImportZuerichIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @Autowired
    private RegionsCsvImportService importer;

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
    void zuerichFileImportsCompletelyAndRepeatsUnchanged() throws Exception {
        String csv = Files.readString(ZuerichRegionsFileTest.FILE, StandardCharsets.UTF_8);

        var dryRun = importer.importCsv(csv, true);
        assertThat(dryRun.rows()).isNotEmpty().allSatisfy(row -> {
            assertThat(row.errors()).as(row.slug()).isEmpty();
            assertThat(row.status()).as(row.slug()).isEqualTo(Status.CREATE);
        });
        int rows = dryRun.rows().size();
        assertThat(dryRun.created()).isEqualTo(rows);
        assertThat(jdbc.queryForObject("select count(*) from region", Integer.class)).isZero();

        var live = importer.importCsv(csv, false);
        assertThat(live.created()).isEqualTo(rows);
        assertThat(live.errors()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from region", Integer.class)).isEqualTo(rows);

        // every region reaches europa through the closure table
        UUID europa = jdbc.queryForObject("select id from region where slug = 'europa'", UUID.class);
        assertThat(jdbc.queryForObject("select count(*) from region_closure where ancestor_id = ?", Integer.class,
                europa)).isEqualTo(rows);
        assertThat(jdbc.queryForObject("select depth from region_closure c join region r on r.id = c.region_id"
                + " where r.slug = 'zuerich-kreis-4' and c.ancestor_id = ?", Integer.class, europa)).isEqualTo(4);
        int expectedPostcodes = dryRun.rows().stream().mapToInt(row -> row.postcodes().size()).sum();
        assertThat(jdbc.queryForObject("select count(*) from region_postcode where country = 'CH'", Integer.class))
                .isEqualTo(expectedPostcodes);
        assertThat(jdbc.queryForObject("select r.slug from region_postcode p join region r on r.id = p.region_id"
                + " where p.postcode = '8004'", String.class)).isEqualTo("zuerich-kreis-4");
        assertThat(jdbc.queryForObject("select count(*) from history_entry where action = 'REGIONS_IMPORTED'",
                Integer.class)).isEqualTo(1);

        var again = importer.importCsv(csv, false);
        assertThat(again.rows()).extracting(Row::status).containsOnly(Status.UNCHANGED);
        assertThat(again.created()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from region", Integer.class)).isEqualTo(rows);
    }
}
