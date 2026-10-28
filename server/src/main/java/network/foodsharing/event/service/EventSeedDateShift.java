package network.foodsharing.event.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded events (seed module 0044) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class EventSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000044-";

    private final JdbcTemplate jdbc;

    public EventSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        shiftColumn(jdbc, SEED_PREFIX, "event", "starts_at", delta);
        shiftColumn(jdbc, SEED_PREFIX, "event", "ends_at", delta);
        shiftColumn(jdbc, SEED_PREFIX, "event_invitation", "responded_at", delta);
        shiftColumn(jdbc, SEED_PREFIX, "event_wall_post", "created_at", delta);
    }

    /** Shifts one timestamp column of the seed rows (ids starting with {@code prefix}). */
    private static void shiftColumn(JdbcTemplate jdbc, String prefix, String table, String column, Duration delta) {
        List<Object[]> rows = jdbc.query("select id, " + column + " from " + table + " where " + column
                + " is not null", (rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getTimestamp(2)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(prefix)) {
                continue;
            }
            Timestamp shifted = Timestamp.from(((Timestamp) row[1]).toInstant().plus(delta));
            jdbc.update("update " + table + " set " + column + " = ? where id = ?", shifted, row[0]);
        }
    }
}
