package network.foodsharing.profile.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded bananas, buddies and profile wall posts (user_account rows are not touched) (seed prefix 00000001-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class ProfileSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000001-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "rating", List.of("created_at"),
            "buddy", List.of("created_at"),
            "user_wall_post", List.of("created_at", "deleted_at"));

    private final JdbcTemplate jdbc;

    public ProfileSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
