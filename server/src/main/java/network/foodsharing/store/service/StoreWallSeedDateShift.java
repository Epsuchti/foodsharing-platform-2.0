package network.foodsharing.store.service;

import java.sql.Timestamp;
import java.time.Duration;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves seeded wall posts ({@code 00000020-0008-…}) forward with the local seed anchor (A13), so "40 days old" stays
 * 40 days old.
 */
@Component
@Profile("local")
public class StoreWallSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000020-0008-";

    private final JdbcTemplate jdbc;

    public StoreWallSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        java.util.List<Object[]> rows = jdbc.query("select id, created_at from store_wall_post",
                (rs, n) -> new Object[] {rs.getObject(1, java.util.UUID.class), rs.getTimestamp(2)});
        for (Object[] row : rows) {
            if (!row[0].toString().startsWith(SEED_PREFIX)) {
                continue;
            }
            Timestamp shifted = Timestamp.from(((Timestamp) row[1]).toInstant().plus(delta));
            jdbc.update("update store_wall_post set created_at = ?, updated_at = ? where id = ?", shifted, shifted,
                    row[0]);
        }
    }
}
