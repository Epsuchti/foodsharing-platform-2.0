package network.foodsharing.intropickup;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.csv.CsvParser;
import network.foodsharing.common.jobs.ProgressTracker;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.service.IntroPickupAdminService;
import network.foodsharing.intropickup.service.NewcomerProgressService;
import network.foodsharing.intropickup.service.PastImportService;
import network.foodsharing.intropickup.service.PastImportService.Reason;
import network.foodsharing.intropickup.service.PastImportService.Row;
import network.foodsharing.intropickup.service.PastImportService.Status;
import network.foodsharing.region.service.MembershipService;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.service.StoresCsvImportService;
import network.foodsharing.testsupport.TestAuth;
import network.foodsharing.testsupport.ZuerichData;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.NewUser;
import network.foodsharing.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
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
 * D16.3: the EinAb table import end-to-end on an empty production schema (PostgreSQL): Zürich regions (D16.1) and
 * stores (D16.2) imported, users created through the service layer, store aliases, preview → confirm, rerun, large
 * file through the job tracker.
 */
@SpringBootTest(properties = {"spring.liquibase.contexts=schema", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Testcontainers
class PastImportZuerichIT {

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
    private UserService users;

    @Autowired
    private MembershipService memberships;

    @Autowired
    private IntroPickupAdminService admin;

    @Autowired
    private PastImportService pastImport;

    @Autowired
    private NewcomerProgressService progress;

    @Autowired
    private ProgressTracker tracker;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void logout() {
        TestAuth.clear();
    }

    private UUID region(String slug) {
        return jdbc.queryForObject("select id from region where slug = ?", UUID.class, slug);
    }

    private User user(String name, long foodsaverNumber) {
        User user = users.create(new NewUser(name + "@example.org", Instant.parse("2026-01-01T00:00:00Z"), name, name,
                null, null, null));
        jdbc.update("update user_account set foodsaver_number = ? where id = ?", foodsaverNumber, user.getId());
        return user;
    }

    @Test
    void zuerichEinAbTableImportsEndToEnd() throws Exception {
        // ORGA with a real account (the importing admin becomes the guide of the imported EinAbs)
        User orga = user("orga", 900000);
        jdbc.update("update user_account set role = 'ORGA' where id = ?", orga.getId());
        TestAuth.login(new SessionPrincipal(orga.getId(), UUID.randomUUID(), Role.ORGA, "orga@example.org"));

        ZuerichData.prepare(regionsImport, categories, chains);
        UUID city = region("zuerich-stadt");
        var stores = storesImport.importCsv(city,
                ZuerichData.read("stores.csv"), false);
        assertThat(stores.created()).isEqualTo(40);

        User cityMember = user("anna", 900001);
        memberships.join(cityMember.getId(), city);
        User kreisMember = user("ben", 900002);
        memberships.join(kreisMember.getId(), region("zuerich-kreis-4"));
        User winterthur = user("cleo", 900003);
        memberships.join(winterthur.getId(), region("winterthur"));

        for (List<String> line : CsvParser.parse(ZuerichData.read("store-aliases.csv"))) {
            if (!CsvParser.isComment(line) && !line.getFirst().equals("alias")) {
                admin.createAlias(city, line.get(0), line.get(1));
            }
        }

        String csv = ZuerichData.read("einabs-sample.csv");
        var preview = pastImport.preview(city, csv);
        List<Row> rows = preview.rows();
        assertThat(rows).extracting(Row::status).containsExactly(Status.CREATE, Status.SKIP_DUPLICATE, Status.CREATE,
                Status.CREATE, Status.CREATE, Status.NEEDS_ATTENTION, Status.NEEDS_ATTENTION, Status.CREATE,
                Status.CREATE);
        assertThat(rows.get(1).reasons()).contains(Reason.DUPLICATE_IN_FILE);
        // umlaut alias "BÄCKEREI MUSTER EINS" matches the table's "Baeckerei Muster Eins"
        assertThat(rows.get(2).store()).isEqualTo("Bäckerei Muster Eins 4");
        assertThat(rows.get(2).reasons()).contains(Reason.STORE_NAME_CORRECTED);
        // member of a Kreis below the city is not "other region"
        assertThat(rows.get(3).reasons()).doesNotContain(Reason.USER_OTHER_REGION);
        assertThat(rows.get(4).reasons()).contains(Reason.NOT_COUNTED);
        assertThat(rows.get(5).reasons()).contains(Reason.USER_OTHER_REGION);
        assertThat(rows.get(6).reasons()).contains(Reason.UNKNOWN_USER);
        assertThat(rows.get(7).category()).isEqualTo(IntroPickupCategory.TAKEOUT);
        assertThat(rows.get(8).store()).isEqualTo("Migros Muster Platz 2");
        assertThat(preview.counts().totalRows()).isEqualTo(9);
        assertThat(preview.counts().willCreate()).isEqualTo(6);
        assertThat(preview.counts().knownUsers()).isEqualTo(3);
        assertThat(preview.counts().unknownUsers()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from intro_pickup", Integer.class)).isZero();

        var result = pastImport.confirm(city, csv);
        assertThat(result.created()).isEqualTo(6);
        assertThat(result.needsAttention()).isEqualTo(2);
        assertThat(result.jobId()).isNull();
        // not imported: the Winterthur member and the unknown number
        assertThat(jdbc.queryForObject("select count(*) from intro_pickup_slot where booking_user_id = ?",
                Integer.class, winterthur.getId())).isZero();
        // day boundary: noon in Europe/Zurich, before and on the daylight-saving switch (29.03.2026)
        assertThat(jdbc.queryForObject("select count(*) from intro_pickup where imported = true and start_at = ?",
                Integer.class, Timestamp.from(Instant.parse("2026-03-03T11:00:00Z")))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from intro_pickup where imported = true and start_at = ?",
                Integer.class, Timestamp.from(Instant.parse("2026-03-29T10:00:00Z")))).isEqualTo(2);

        // counted bookings reach the newcomer progress (the "x" row does not count)
        assertThat(progress.progressOf(cityMember.getId(), city).counted()).isEqualTo(3);
        assertThat(progress.progressOf(kreisMember.getId(), city).counted()).isEqualTo(2);

        var again = pastImport.confirm(city, csv);
        assertThat(again.created()).isZero();
        assertThat(again.skipped()).isEqualTo(7);

        // a large file runs through the job tracker and finishes
        StringBuilder large = new StringBuilder("ID;Datum;No.;Betrieb;Kategorie;EinAb-Geber;FT-Besuch;Feedback\n");
        LocalDate day = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 600; i++) {
            LocalDate date = day.plusDays(i);
            large.append(i % 2 == 0 ? "900001" : "900002").append(';').append(date.getDayOfMonth()).append('.')
                    .append(date.getMonthValue()).append('.').append(date.getYear()).append(";1;Laden ").append(i)
                    .append(";Supermarkt;Guide;nein;ok\n");
        }
        var big = pastImport.confirm(city, large.toString());
        assertThat(big.created()).isEqualTo(600);
        assertThat(big.jobId()).isNotNull();
        assertThat(tracker.find(big.jobId())).hasValueSatisfying(job -> {
            assertThat(job.status()).isEqualTo(ProgressTracker.Status.DONE);
            assertThat(job.done()).isEqualTo(600);
        });
        assertThat(pastImport.confirm(city, large.toString()).created()).isZero();
    }
}
