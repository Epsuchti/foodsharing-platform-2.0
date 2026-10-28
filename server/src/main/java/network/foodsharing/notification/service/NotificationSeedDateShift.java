package network.foodsharing.notification.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import network.foodsharing.common.seed.SeedDateShiftContributor;
import network.foodsharing.common.seed.SeedDateShifts;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Moves the seeded bells, outbox mails, newsletters and push subscriptions (seed prefix 00000004-) forward with the local seed anchor (A13). */
@Component
@Profile("local")
public class NotificationSeedDateShift implements SeedDateShiftContributor {

    static final String SEED_PREFIX = "00000004-";

    static final Map<String, List<String>> COLUMNS = Map.of(
            "bell", List.of("created_at", "read_at", "deleted_at", "pushed_at"),
            "mail_outbox", List.of("created_at", "next_attempt_at", "sent_at", "rate_limited_until"),
            "newsletter", List.of("created_at", "queued_at", "sent_at"),
            "push_subscription", List.of("created_at", "last_success_at"));

    private final JdbcTemplate jdbc;

    public NotificationSeedDateShift(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void shift(Duration delta) {
        SeedDateShifts.shift(jdbc, SEED_PREFIX, delta, COLUMNS);
    }
}
