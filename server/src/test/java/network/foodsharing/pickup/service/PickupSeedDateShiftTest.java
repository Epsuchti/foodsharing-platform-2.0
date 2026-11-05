package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Seeded pickups of one store three days apart shift by three days without hitting unique (store_id, pickup_at). */
class PickupSeedDateShiftTest {

    private static final UUID STORE = UUID.fromString("00000020-0001-4000-8000-000000000003");
    private static final UUID EARLY = UUID.fromString("00000021-0002-4000-8000-000000000001");
    private static final UUID LATE = UUID.fromString("00000021-0002-4000-8000-000000000007");
    private static final Instant EARLY_AT = Instant.parse("2026-11-03T17:30:00Z");
    private static final Instant LATE_AT = Instant.parse("2026-11-06T17:30:00Z");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:pickup-seed-shift-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        jdbc.execute("create table pickup (id uuid primary key, store_id uuid not null, "
                + "pickup_at timestamp with time zone not null, constraint uq_pickup_store_id_pickup_at "
                + "unique (store_id, pickup_at))");
        jdbc.execute("create table pickup_signup (id uuid primary key, pickup_at timestamp with time zone, "
                + "confirmed_at timestamp with time zone, reminded_at timestamp with time zone)");
        jdbc.execute("create table pickup_day_conversation (id uuid primary key, pickup_date date not null)");
        // inserted earliest first, so an unordered shift would move EARLY onto LATE's instant first
        jdbc.update("insert into pickup values (?, ?, ?)", EARLY, STORE, Timestamp.from(EARLY_AT));
        jdbc.update("insert into pickup values (?, ?, ?)", LATE, STORE, Timestamp.from(LATE_AT));
    }

    @Test
    void forwardShiftMovesTheLatestRowFirst() {
        new PickupSeedDateShift(jdbc).shift(Duration.ofDays(3));

        assertThat(pickupAt(EARLY)).isEqualTo(LATE_AT);
        assertThat(pickupAt(LATE)).isEqualTo(LATE_AT.plus(Duration.ofDays(3)));
    }

    @Test
    void backwardShiftMovesTheEarliestRowFirst() {
        new PickupSeedDateShift(jdbc).shift(Duration.ofDays(-3));

        assertThat(pickupAt(EARLY)).isEqualTo(EARLY_AT.minus(Duration.ofDays(3)));
        assertThat(pickupAt(LATE)).isEqualTo(EARLY_AT);
    }

    private Instant pickupAt(UUID id) {
        return jdbc.queryForObject("select pickup_at from pickup where id = ?", Timestamp.class, id).toInstant();
    }
}
