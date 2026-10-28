package network.foodsharing.profile.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import network.foodsharing.user.service.CommitmentSource;
import org.junit.jupiter.api.Test;

/** Weekly buckets (Monday, Europe/Zurich) of the profile commitments. */
class CommitmentsServiceTest {

    @Test
    void bucketsPerWeekAndKind() {
        LocalDate first = LocalDate.of(2026, 10, 12); // Monday
        List<CommitmentSource.Commitment> list = List.of(
                c(CommitmentSource.Kind.PICKUP, "2026-10-12T00:30:00+02:00"),
                c(CommitmentSource.Kind.PICKUP, "2026-10-18T23:30:00+02:00"),
                c(CommitmentSource.Kind.EVENT_CREATED, "2026-10-19T08:00:00+02:00"),
                c(CommitmentSource.Kind.BASKET_OFFERED, "2026-11-08T12:00:00+01:00"),
                c(CommitmentSource.Kind.PICKUP, "2026-10-11T23:30:00+02:00"),
                c(CommitmentSource.Kind.PICKUP, "2026-11-09T00:30:00+01:00"));
        List<CommitmentsService.Week> weeks = CommitmentsService.bucket(first, 4, list);
        assertThat(weeks).extracting(CommitmentsService.Week::weekStart).containsExactly(first, first.plusWeeks(1),
                first.plusWeeks(2), first.plusWeeks(3));
        assertThat(weeks.get(0).pickups()).isEqualTo(2);
        assertThat(weeks.get(1).eventsCreated()).isEqualTo(1);
        assertThat(weeks.get(3).basketsOffered()).isEqualTo(1);
        assertThat(weeks.stream().mapToInt(CommitmentsService.Week::pickups).sum()).isEqualTo(2);
    }

    private static CommitmentSource.Commitment c(CommitmentSource.Kind kind, String at) {
        return new CommitmentSource.Commitment(kind, java.time.OffsetDateTime.parse(at).toInstant());
    }
}
