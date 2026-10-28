package network.foodsharing.report.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded reports, report walls and mediation requests (seed prefix 00000050-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class ReportSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000050-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "report", List.of("created_at", "reminder_at", "deleted_at"),
            "report_wall_post", List.of("created_at", "deleted_at"),
            "mediation_request", List.of("created_at"));

    private final JdbcTemplate jdbc;

    public ReportSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
