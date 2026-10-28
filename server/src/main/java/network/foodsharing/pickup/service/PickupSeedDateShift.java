package network.foodsharing.pickup.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves seeded one-time pickups and sign-ups (ids of seed module 0021) forward with the local seed anchor (A13), so
 * the seeded upcoming pickups stay upcoming. Regular pickups are weekly and are not shifted; every seeded future
 * sign-up has its own {@code pickup} row, so after a shift that is not a whole number of weeks it simply shows as a
 * one-time pickup next to the regular occurrences.
 */
@Component
@Profile("local")
public class PickupSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000021-";

    private final JdbcTemplate jdbc;

    public PickupSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        shiftColumn("pickup", "pickup_at", delta);
        shiftColumn("pickup_signup", "pickup_at", delta);
        shiftColumn("pickup_signup", "confirmed_at", delta);
        shiftColumn("pickup_signup", "reminded_at", delta);
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
