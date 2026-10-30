package network.foodsharing.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import network.foodsharing.config.MutableClock;
import network.foodsharing.notification.mail.CapturingMailTransport;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * e2e profile on H2: a reset restores the seed state (twice in a row, counts stable, every table row by row equal to
 * the state after startup), mails and the clock. The reset replays the {@link SeedSnapshot} taken at startup; its
 * duration is logged ("Dev reset done in …"), not asserted.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed-reset-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class SeedResetServiceIT {

    private static final String COUNTS = "select (select count(*) from user_account) as users, "
            + "(select count(*) from history_entry) as history, (select count(*) from auth_session) as sessions";

    @Autowired
    private SeedResetService seedResetService;

    @Autowired
    private DevService devService;

    @Autowired
    private CapturingMailTransport mails;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private network.foodsharing.config.RenewableLockProvider lockProvider;

    @Autowired
    private SeedSnapshot snapshot;

    @Autowired
    private network.foodsharing.upload.service.UploadSeedFiles uploadSeedFiles;

    /** Every table after startup (the class has its own database, so no other test touched it yet). */
    private static Map<String, List<String>> started;

    @BeforeAll
    static void rememberTheStartedState(@Autowired JdbcTemplate jdbc) {
        started = tables(jdbc);
    }

    @Test
    void resetRestoresEveryTableAsAfterStartupFromTheStartupSnapshot() {
        assertThat(snapshot.available()).isTrue();
        assertThat(started).containsKeys("USER_ACCOUNT", "DATABASECHANGELOG", "UPLOAD");

        for (int round = 0; round < 2; round++) {
            jdbc.update("update user_account set display_name = 'Changed' where id = ?", SeedUsers.FOODSAVER.id());
            jdbc.update("delete from history_entry");
            jdbc.update("update upload set variants = false");
            jdbc.execute("create table stray_table (id int)");

            seedResetService.reset();
            uploadSeedFiles.resetInMemoryState(); // as on startup: marks the variants of the seeded images

            Map<String, List<String>> now = tables(jdbc);
            assertThat(now.keySet()).isEqualTo(started.keySet());
            // written by jobs since startup, not by the changelog: search index (SearchReindexJob), job locks and runs
            now.keySet().stream().filter(table -> !List.of("SEARCH_INDEX", "SHEDLOCK", "JOB_RUN").contains(table))
                    .forEach(table -> assertThat(now.get(table)).as(table).isEqualTo(started.get(table)));
            // the snapshot predates the start-up index build: the reset rebuilds it (SeedDerivedData), same entries
            assertThat(started.get("SEARCH_INDEX")).isNotEmpty();
            assertThat(jdbc.queryForList("select kind || ':' || entity_id from search_index", String.class))
                    .containsExactlyInAnyOrderElementsOf(jdbc.queryForList(
                            "select distinct kind || ':' || entity_id from search_index", String.class))
                    .hasSize(started.get("SEARCH_INDEX").size());
        }
    }

    private static Map<String, List<String>> tables(JdbcTemplate jdbc) {
        Map<String, List<String>> tables = new TreeMap<>();
        for (String table : jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'", String.class)) {
            tables.put(table, jdbc.query("select * from \"" + table + "\"", (rs, row) -> {
                StringBuilder line = new StringBuilder();
                for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                    Object value = rs.getObject(column);
                    if (value instanceof java.sql.Array array) {
                        value = java.util.Arrays.deepToString((Object[]) array.getArray());
                    } else if (value instanceof byte[] bytes) {
                        value = java.util.Arrays.toString(bytes);
                    }
                    line.append(value).append('|');
                }
                return line.toString();
            }).stream().sorted().toList());
        }
        return tables;
    }

    /**
     * {@code app.scheduling.enabled=false} means no {@code @Scheduled} task at all: Spring Modulith Moments used to
     * switch scheduling on anyway, and jobs then wrote history in the middle of these tests and hit the dropped
     * {@code shedlock} table during resets.
     */
    @Test
    void noScheduledTaskRunsWhenSchedulingIsOff(
            @Autowired org.springframework.context.ApplicationContext context) {
        assertThat(context.getBeansOfType(org.springframework.scheduling.config.ScheduledTaskHolder.class).values())
                .flatExtracting(holder -> List.copyOf(holder.getScheduledTasks())).isEmpty();
    }

    @Test
    void scheduledJobLocksStillWorkAfterAReset() {
        var config = new net.javacrumbs.shedlock.core.LockConfiguration(Instant.now(), "seed-reset-lock-test",
                java.time.Duration.ofMillis(10), java.time.Duration.ZERO);
        lockProvider.lock(config).orElseThrow().unlock();
        seedResetService.reset();
        // without renewing the provider's row cache the update hits the recreated, empty table and returns empty
        var again = lockProvider.lock(config);
        assertThat(again).isPresent();
        again.get().unlock(); // a held lock would make later resets wait for it (as for a running job)
    }

    @Test
    void resetRestoresTheSeedStateAndIsRepeatable() {
        assertThat(seedResetService.isH2()).isTrue();
        Map<String, Object> seeded = jdbc.queryForMap(COUNTS);
        assertThat(((Number) seeded.get("USERS")).intValue()).isEqualTo(SeedUsers.values().length);

        jdbc.update("update user_account set display_name = 'Changed' where id = ?", SeedUsers.FOODSAVER.id());
        jdbc.update("delete from history_entry where subject_id = ?", SeedUsers.ORGA.id());
        jdbc.update("insert into user_account (id, version, created_at, updated_at, email, display_name, "
                + "preferred_language, theme, role, verified, active, registered_at) values "
                + "(random_uuid(), 0, now(), now(), 'extra@local', 'Extra', 'DE', 'SYSTEM', 'FOODSHARER', false, true, now())");

        seedResetService.reset();
        assertThat(jdbc.queryForMap(COUNTS)).isEqualTo(seeded);
        assertThat(jdbc.queryForObject("select display_name from user_account where id = ?", String.class,
                SeedUsers.FOODSAVER.id())).isEqualTo("Sarah Müller");

        seedResetService.reset();
        assertThat(jdbc.queryForMap(COUNTS)).isEqualTo(seeded);
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog where author = 'platform'",
                Integer.class)).isPositive();
    }

    @Test
    void resetAllAlsoClearsMailsAndRestoresTheConfiguredClock() {
        mails.send("newbie@local", "Hello", "Visit http://localhost:8081/login/verify?token=abc", null);
        clock.fixAt(Instant.parse("2030-01-01T00:00:00Z"));

        devService.resetAll();

        assertThat(mails.list(null)).isEmpty();
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"));
        assertThat(devService.listDevUsers()).extracting(view -> view.user().getEmail())
                .containsExactlyElementsOf(List.of(SeedUsers.values()).stream().map(SeedUsers::email).toList());
    }
}
