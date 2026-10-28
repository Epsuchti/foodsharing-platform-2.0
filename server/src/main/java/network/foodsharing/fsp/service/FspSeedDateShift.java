package network.foodsharing.fsp.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded food share points and their walls (seed prefix 00000049-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class FspSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000049-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "food_share_point", List.of("created_at", "deleted_at"),
            "fsp_wall_post", List.of("created_at", "deleted_at"));

    private final JdbcTemplate jdbc;

    public FspSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
