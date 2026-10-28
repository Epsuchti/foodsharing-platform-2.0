package network.foodsharing.statistics.service;

import java.sql.Date;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves the seeded daily statistics (seed module 0054) forward with the local seed anchor (A13). Rows are moved
 * newest first so the unique day never collides; a seeded day that a real aggregation row already occupies is dropped.
 */
@Component
@Profile("local")
public class StatisticsSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000054-";

    private final JdbcTemplate jdbc;

    public StatisticsSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        long days = delta.toDays();
        if (days == 0) {
            return;
        }
        List<Object[]> rows = jdbc.query("select id, stat_day from statistics_daily order by stat_day "
                + (days > 0 ? "desc" : "asc"), (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getDate(2)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            Date shifted = Date.valueOf(((Date) row[1]).toLocalDate().plusDays(days));
            Integer taken = jdbc.queryForObject("select count(*) from statistics_daily where stat_day = ?",
                    Integer.class, shifted);
            if (taken != null && taken > 0) {
                jdbc.update("delete from statistics_daily where id = ?", row[0]);
            } else {
                jdbc.update("update statistics_daily set stat_day = ? where id = ?", shifted, row[0]);
            }
        }
    }
}
