package network.foodsharing.resource.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded shared resources (seed prefix 00000056-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class ResourceSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000056-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "resource", List.of("created_at", "deleted_at"));

    private final JdbcTemplate jdbc;

    public ResourceSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
