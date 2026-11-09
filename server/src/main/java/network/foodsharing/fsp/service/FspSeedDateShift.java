package network.foodsharing.fsp.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Moves the seeded food share points, their walls and cleaning shifts (seed prefix 00000049-) forward with the local
 * seed anchor (A13). A shifted cleaning sign-up keeps its own shift row, so it shows as a one-time shift when the
 * shift is not a whole number of weeks (as pickups do).
 */
@Component
@Profile({"local", "staging"})
public class FspSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000049-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "food_share_point", List.of("created_at", "deleted_at"),
            "fsp_wall_post", List.of("created_at", "deleted_at"),
            "fsp_cleaning_shift", List.of("shift_at"),
            "fsp_cleaning_signup", List.of("created_at", "shift_at", "confirmed_at"));

    private final JdbcTemplate jdbc;

    public FspSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
