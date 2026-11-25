package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Seed metric days and excluded dates shift without hitting their unique keys, also when real staging rows (the
 * nightly archive writes them for seed regions) already hold the target day: the seed row is dropped then.
 */
class AutomationSeedDateShiftTest {

    private static final UUID REGION = UUID.fromString("00000010-0001-4000-8000-000000000040");
    private static final UUID POLICY = UUID.fromString("00000022-0001-4000-8000-000000000001");
    private static final String KEY = "SLOT_APPROVAL.CONFIRMED";

    private JdbcTemplate jdbc;
    private AutomationSeedDateShift shift;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:automation-seed-shift-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1"));
        jdbc.execute("create table metric_daily_count (id uuid primary key, region_id uuid not null, "
                + "metric_day date not null, metric_key varchar(80) not null, metric_value bigint not null, "
                + "constraint uq_metric_daily_count unique (region_id, metric_day, metric_key))");
        jdbc.execute("create table pickup_policy_excluded_date (id uuid primary key, policy_id uuid not null, "
                + "excluded_date date not null, constraint uq_excluded unique (policy_id, excluded_date))");
        shift = new AutomationSeedDateShift(jdbc);
    }

    @Test
    void seedMetricRowsMoveLatestFirst() {
        UUID early = seedId(1);
        UUID late = seedId(2);
        metric(early, LocalDate.of(2026, 11, 3));
        metric(late, LocalDate.of(2026, 11, 6));

        shift.shiftMetricDays(3);

        assertThat(metricDay(early)).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(metricDay(late)).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void seedMetricRowOnADayARealRowHoldsIsDropped() {
        UUID seed = seedId(1);
        UUID real = UUID.randomUUID();
        metric(seed, LocalDate.of(2026, 11, 3));
        metric(real, LocalDate.of(2026, 11, 10));

        shift.shiftMetricDays(7);

        assertThat(jdbc.queryForObject("select count(*) from metric_daily_count where id = ?", Integer.class, seed))
                .isZero();
        assertThat(metricDay(real)).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void excludedDatesMoveLatestFirstAndYieldToRealRows() {
        UUID early = seedId(1);
        UUID late = seedId(2);
        UUID blocked = seedId(3);
        excluded(early, LocalDate.of(2026, 11, 8));
        excluded(late, LocalDate.of(2026, 11, 9));
        excluded(blocked, LocalDate.of(2026, 11, 20));
        excluded(UUID.randomUUID(), LocalDate.of(2026, 11, 21));

        shift.shiftExcludedDates(Duration.ofDays(1));

        assertThat(excludedDate(early)).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(excludedDate(late)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(jdbc.queryForObject("select count(*) from pickup_policy_excluded_date where id = ?",
                Integer.class, blocked)).isZero();
    }

    private static UUID seedId(int n) {
        return UUID.fromString(String.format("00000022-0009-4000-8000-%012d", n));
    }

    private void metric(UUID id, LocalDate day) {
        jdbc.update("insert into metric_daily_count values (?, ?, ?, ?, 1)", id, REGION, Date.valueOf(day), KEY);
    }

    private void excluded(UUID id, LocalDate day) {
        jdbc.update("insert into pickup_policy_excluded_date values (?, ?, ?)", id, POLICY, Date.valueOf(day));
    }

    private LocalDate metricDay(UUID id) {
        return jdbc.queryForObject("select metric_day from metric_daily_count where id = ?", Date.class, id)
                .toLocalDate();
    }

    private LocalDate excludedDate(UUID id) {
        return jdbc.queryForObject("select excluded_date from pickup_policy_excluded_date where id = ?", Date.class,
                id).toLocalDate();
    }
}
