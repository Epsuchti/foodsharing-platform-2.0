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
        for (String column : List.of("created_at", "updated_at", "pickup_at", "telegram_deleted_at", "sent_at",
                "filled_at")) {
            shiftTimestamp("open_slot_advert_audit", column, delta);
        }
        for (String column : List.of("created_at", "updated_at", "pickup_at", "cancelled_at", "immediate_notified_at",
                "final_checked_at", "final_notified_at")) {
            shiftTimestamp("signout_cancellation", column, delta);
        }
        shiftTimestamp("signout_alert_setting", "last_checked_at", delta);
        for (String column : List.of("created_at", "updated_at", "pickup_at")) {
            shiftTimestamp("pickup_extra_slot", column, delta);
        }
        long days = delta.toDays();
        if (days != 0) {
            // Row by row, latest first when moving forward, so (region, day, key) stays unique on Postgres.
            List<Object[]> metricRows = jdbc.query("select id, metric_day from metric_daily_count order by metric_day "
                    + (days > 0 ? "desc" : "asc"), (rs, n) -> new Object[] {rs.getObject(1, UUID.class),
                    rs.getDate(2)});
            for (Object[] row : metricRows) {
                if (row[0].toString().startsWith(SEED_PREFIX)) {
                    jdbc.update("update metric_daily_count set metric_day = ? where id = ?",
                            Date.valueOf(((Date) row[1]).toLocalDate().plusDays(days)), row[0]);
                }
            }
        }
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
