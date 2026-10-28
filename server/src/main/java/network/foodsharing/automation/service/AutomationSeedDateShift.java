package network.foodsharing.automation.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves seeded automation rows (ids of seed module 0022) forward with the local seed anchor (A13): audit times and
 * pickup times, and the excluded dates by whole days, so "the last 7 days" and "next Sunday" stay true.
 */
@Component
@Profile("local")
public class AutomationSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000022-";

    private final JdbcTemplate jdbc;

    public AutomationSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        for (String column : List.of("created_at", "updated_at", "pickup_at")) {
            shiftTimestamp("pickup_policy_audit", column, delta);
        }
        shiftTimestamp("request_policy_audit", "created_at", delta);
        shiftTimestamp("request_policy_audit", "updated_at", delta);
        // The excluded date follows the seeded one-time pickup on that day (10:00 Zurich), which moves by delta.
        java.time.ZoneId zurich = java.time.ZoneId.of("Europe/Zurich");
        List<Object[]> rows = jdbc.query("select id, excluded_date from pickup_policy_excluded_date",
                (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getDate(2)});
        for (Object[] row : rows) {
            if (row[0].toString().startsWith(SEED_PREFIX)) {
                java.time.LocalDate moved = ((Date) row[1]).toLocalDate().atTime(10, 0).atZone(zurich).toInstant()
                        .plus(delta).atZone(zurich).toLocalDate();
                jdbc.update("update pickup_policy_excluded_date set excluded_date = ? where id = ?",
                        Date.valueOf(moved), row[0]);
            }
        }
    }

    private void shiftTimestamp(String table, String column, Duration delta) {
        List<Object[]> rows = jdbc.query("select id, " + column + " from " + table + " where " + column
                + " is not null", (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getTimestamp(2)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            Timestamp shifted = Timestamp.from(((Timestamp) row[1]).toInstant().plus(delta));
            jdbc.update("update " + table + " set " + column + " = ? where id = ?", shifted, row[0]);
        }
    }
}
