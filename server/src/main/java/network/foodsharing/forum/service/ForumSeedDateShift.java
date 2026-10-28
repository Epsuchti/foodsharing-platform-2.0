package network.foodsharing.forum.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded forum threads and posts (seed prefix 00000043-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class ForumSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000043-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "forum_thread", List.of("created_at", "last_post_at", "deleted_at"),
            "forum_post", List.of("created_at", "edited_at", "hidden_at", "deleted_at"));

    private final JdbcTemplate jdbc;

    public ForumSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
