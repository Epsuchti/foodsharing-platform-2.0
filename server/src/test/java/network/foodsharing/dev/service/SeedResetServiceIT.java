package network.foodsharing.dev.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import network.foodsharing.config.MutableClock;
import network.foodsharing.notification.mail.CapturingMailTransport;
import network.foodsharing.testsupport.SeedUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** e2e profile on H2: a reset restores the seed state (twice in a row, counts stable), mails and the clock. */
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
