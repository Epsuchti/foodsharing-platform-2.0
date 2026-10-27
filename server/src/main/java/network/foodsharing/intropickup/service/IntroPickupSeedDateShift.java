package network.foodsharing.intropickup.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves the seeded EinAbs (ids of seed module 0030) forward with the local seed anchor, so the "upcoming" seed
 * EinAbs stay upcoming and the past ones keep their distance (docs/conventions.md A13). The new values are computed in
 * Java, so the statements are plain and portable (PostgreSQL and H2).
 */
@Component
@Profile("local")
public class IntroPickupSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000030-";

    private final JdbcTemplate jdbc;

    public IntroPickupSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        shiftColumn("intro_pickup", "start_at", delta);
        shiftColumn("intro_pickup_slot", "booked_at", delta);
        shiftColumn("intro_pickup_slot", "done_at", delta);
    }

    private void shiftColumn(String table, String column, Duration delta) {
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
