package network.foodsharing.achievement.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded awarded achievements (validity moves along) (seed prefix 00000051-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class AchievementSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000051-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "awarded_achievement", List.of("created_at", "awarded_at", "valid_until:date", "revoked_at"));

    private final JdbcTemplate jdbc;

    public AchievementSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
