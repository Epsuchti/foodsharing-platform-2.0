package network.foodsharing.automation.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Profile({"local", "staging"})
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
        for (String column : List.of("created_at", "updated_at", "pickup_at", "deadline_at")) {
            shiftTimestamp("pickup_link_pending", column, delta);
        }
        shiftMetricDays(delta.toDays());
        shiftExcludedDates(delta);
    }

    /**
     * Seed metric rows move by whole days, latest first when moving forward, so (region, day, key) stays unique among
     * them. On staging the nightly archive also writes real rows for seed regions (from the seeded audits); a seed row
     * whose target (region, day, key) is already taken by such a row is dropped instead of crashing the start.
     */
    void shiftMetricDays(long days) {
        if (days == 0) {
            return;
        }
        List<Object[]> metricRows = jdbc.query("select id, region_id, metric_day, metric_key from metric_daily_count "
                + "order by metric_day " + (days > 0 ? "desc" : "asc"), (rs, n) -> new Object[] {
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getDate(3), rs.getString(4)});
        for (Object[] row : metricRows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            Date shifted = Date.valueOf(((Date) row[2]).toLocalDate().plusDays(days));
            Integer taken = jdbc.queryForObject("select count(*) from metric_daily_count where region_id = ? "
                    + "and metric_day = ? and metric_key = ? and id <> ?", Integer.class, row[1], shifted, row[3],
                    row[0]);
            if (taken != null && taken > 0) {
                jdbc.update("delete from metric_daily_count where id = ?", row[0]);
            } else {
                jdbc.update("update metric_daily_count set metric_day = ? where id = ?", shifted, row[0]);
            }
        }
    }

    /**
     * The excluded date follows the seeded one-time pickup on that day (10:00 Zurich), which moves by delta. Latest
     * first when moving forward so (policy, date) stays unique among seed rows; a target date a real row of the same
     * policy already holds drops the seed row.
     */
    void shiftExcludedDates(Duration delta) {
        if (delta.isZero()) {
            return;
        }
        ZoneId zurich = ZoneId.of("Europe/Zurich");
        List<Object[]> rows = jdbc.query("select id, policy_id, excluded_date from pickup_policy_excluded_date "
                + "order by excluded_date " + (delta.isNegative() ? "asc" : "desc"), (rs, n) -> new Object[] {
                    rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getDate(3)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            LocalDate moved = ((Date) row[2]).toLocalDate().atTime(10, 0).atZone(zurich).toInstant().plus(delta)
                    .atZone(zurich).toLocalDate();
            Integer taken = jdbc.queryForObject("select count(*) from pickup_policy_excluded_date where policy_id = ? "
                    + "and excluded_date = ? and id <> ?", Integer.class, row[1], Date.valueOf(moved), row[0]);
            if (taken != null && taken > 0) {
                jdbc.update("delete from pickup_policy_excluded_date where id = ?", row[0]);
            } else {
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
