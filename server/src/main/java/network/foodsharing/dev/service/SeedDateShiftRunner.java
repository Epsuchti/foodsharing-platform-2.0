package network.foodsharing.dev.service;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the local seed data current (docs/conventions.md A13): on every start of the {@code local} profile, all
 * {@link SeedDateShiftContributor}s move their seed timestamps forward by the whole days between the last anchor
 * (initially {@link #SEED_NOW_DATE}) and today in Europe/Zurich; the new anchor is stored in
 * {@code seed_shift_state}, so repeated starts shift only by the remaining delta. Not active in {@code e2e}, where
 * the clock is fixed at SEED_NOW.
 */
@Component
@Profile("local")
public class SeedDateShiftRunner implements ApplicationRunner {

    public static final LocalDate SEED_NOW_DATE = LocalDate.of(2026, 11, 2);
    static final UUID STATE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final Logger log = LoggerFactory.getLogger(SeedDateShiftRunner.class);

    private final JdbcTemplate jdbc;
    private final List<SeedDateShiftContributor> contributors;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public SeedDateShiftRunner(JdbcTemplate jdbc, List<SeedDateShiftContributor> contributors,
                               TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.contributors = contributors;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        shiftIfNeeded();
    }

    /** @return the number of days shifted (0 when already current) */
    public long shiftIfNeeded() {
        List<LocalDate> anchors = jdbc.query("select last_shift_anchor from seed_shift_state where id = ?",
                (rs, row) -> rs.getDate(1).toLocalDate(), STATE_ID);
        LocalDate anchor = anchors.isEmpty() ? SEED_NOW_DATE : anchors.getFirst();
        LocalDate today = clock.instant().atZone(ZONE).toLocalDate();
        long days = ChronoUnit.DAYS.between(anchor, today);
        if (days <= 0) {
            return 0;
        }
        transaction.executeWithoutResult(status -> {
            Duration delta = Duration.ofDays(days);
            contributors.forEach(contributor -> contributor.shift(delta));
            if (anchors.isEmpty()) {
                jdbc.update("insert into seed_shift_state (id, version, created_at, updated_at, last_shift_anchor) "
                        + "values (?, 0, ?, ?, ?)", STATE_ID, java.sql.Timestamp.from(clock.instant()),
                        java.sql.Timestamp.from(clock.instant()), Date.valueOf(today));
            } else {
                jdbc.update("update seed_shift_state set last_shift_anchor = ?, updated_at = ?, version = version + 1 "
                        + "where id = ?", Date.valueOf(today), java.sql.Timestamp.from(clock.instant()), STATE_ID);
            }
        });
        log.info("Shifted local seed data by {} days ({} contributors)", days, contributors.size());
        return days;
    }
}
