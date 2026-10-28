package network.foodsharing.survey.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded surveys and responses (seed prefix 00000046-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class SurveySeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000046-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "survey", List.of("created_at"),
            "survey_response", List.of("created_at"));

    private final JdbcTemplate jdbc;

    public SurveySeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
