package network.foodsharing.basket.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded food baskets and requests (active baskets stay unexpired) (seed prefix 00000048-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class BasketSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000048-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "basket", List.of("created_at", "expires_at", "taken_at"),
            "basket_request", List.of("created_at", "requested_at", "answered_at"));

    private final JdbcTemplate jdbc;

    public BasketSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
