package network.foodsharing.content.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded blog posts (seed prefix 00000012-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class BlogSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000012-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "blog_post", List.of("created_at", "published_at", "deleted_at"));

    private final JdbcTemplate jdbc;

    public BlogSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
