package network.foodsharing.common.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import network.foodsharing.testsupport.JpaSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Only seed rows move; DATE columns move by days; every contributor's tables and columns exist in the schema. */
@JpaSliceTest
class SeedDateShiftsTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shiftsOnlyRowsWithTheSeedPrefix() {
        jdbc.execute("create table shift_probe (id uuid primary key, happened_at timestamp with time zone, "
                + "valid_until date)");
        UUID seed = UUID.fromString("00000099-0001-4000-8000-000000000001");
        UUID other = UUID.randomUUID();
        Instant at = Instant.parse("2026-11-02T09:00:00Z");
        jdbc.update("insert into shift_probe values (?, ?, ?)", seed, java.sql.Timestamp.from(at),
                java.sql.Date.valueOf(LocalDate.of(2026, 11, 30)));
        jdbc.update("insert into shift_probe values (?, ?, ?)", other, java.sql.Timestamp.from(at), null);

        SeedDateShifts.shift(jdbc, "00000099-", Duration.ofDays(3),
                Map.of("shift_probe", List.of("happened_at", "valid_until:date")));

        assertThat(instant(seed)).isEqualTo(at.plus(Duration.ofDays(3)));
        assertThat(instant(other)).isEqualTo(at);
        assertThat(jdbc.queryForObject("select valid_until from shift_probe where id = ?", java.sql.Date.class, seed)
                .toLocalDate()).isEqualTo(LocalDate.of(2026, 12, 3));
        jdbc.execute("drop table shift_probe");
    }

    @Test
    void everyContributorMatchesTheSchema() {
        List<Function<JdbcTemplate, SeedDateShiftContributor>> contributors = List.of(
                network.foodsharing.forum.service.ForumSeedDateShift::new,
                network.foodsharing.basket.service.BasketSeedDateShift::new,
                network.foodsharing.fsp.service.FspSeedDateShift::new,
                network.foodsharing.report.service.ReportSeedDateShift::new,
                network.foodsharing.achievement.service.AchievementSeedDateShift::new,
                network.foodsharing.content.service.BlogSeedDateShift::new,
                network.foodsharing.notification.service.NotificationSeedDateShift::new,
                network.foodsharing.support.service.SupportSeedDateShift::new,
                network.foodsharing.resource.service.ResourceSeedDateShift::new,
                network.foodsharing.profile.service.ProfileSeedDateShift::new,
                network.foodsharing.survey.service.SurveySeedDateShift::new);
        for (Function<JdbcTemplate, SeedDateShiftContributor> factory : contributors) {
            SeedDateShiftContributor contributor = factory.apply(jdbc);
            assertThatCode(() -> contributor.shift(Duration.ofDays(1))).as(contributor.getClass().getSimpleName())
                    .doesNotThrowAnyException();
        }
    }

    private Instant instant(UUID id) {
        return jdbc.queryForObject("select happened_at from shift_probe where id = ?", java.sql.Timestamp.class, id)
                .toInstant();
    }
}
