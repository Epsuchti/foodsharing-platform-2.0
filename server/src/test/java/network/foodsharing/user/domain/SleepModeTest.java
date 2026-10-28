package network.foodsharing.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** A39 sleep status matrix with fixed dates. */
class SleepModeTest {

    static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    static final LocalDate UNTIL = LocalDate.of(2026, 11, 15);

    @Test
    void sleepingMatrix() {
        assertThat(SleepRules.isSleeping(SleepStatus.NONE, FROM, UNTIL, FROM)).isFalse();
        assertThat(SleepRules.isSleeping(SleepStatus.FULL, null, null, FROM)).isTrue();
        assertThat(SleepRules.isSleeping(SleepStatus.TEMPORARY, FROM, UNTIL, FROM.minusDays(1))).isFalse();
        assertThat(SleepRules.isSleeping(SleepStatus.TEMPORARY, FROM, UNTIL, FROM)).isTrue();
        assertThat(SleepRules.isSleeping(SleepStatus.TEMPORARY, FROM, UNTIL, UNTIL)).isTrue();
        assertThat(SleepRules.isSleeping(SleepStatus.TEMPORARY, FROM, UNTIL, UNTIL.plusDays(1))).isFalse();
        assertThat(SleepRules.isSleeping(SleepStatus.TEMPORARY, null, UNTIL, FROM.minusDays(30))).isTrue();
    }

    @Test
    void dateValidation() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        assertThat(SleepRules.validDates(SleepStatus.FULL, null, null, today)).isTrue();
        assertThat(SleepRules.validDates(SleepStatus.NONE, UNTIL, FROM, today)).isTrue();
        assertThat(SleepRules.validDates(SleepStatus.TEMPORARY, FROM, UNTIL, today)).isTrue();
        assertThat(SleepRules.validDates(SleepStatus.TEMPORARY, null, UNTIL, today)).isFalse();
        assertThat(SleepRules.validDates(SleepStatus.TEMPORARY, UNTIL, FROM, today)).isFalse();
        assertThat(SleepRules.validDates(SleepStatus.TEMPORARY, FROM, FROM, today)).isFalse();
    }

    @Test
    void maintenanceWakesExpiredTemporarySleepers() {
        LocalDate today = LocalDate.of(2026, 11, 16);
        assertThat(SleepRules.expired(SleepStatus.TEMPORARY, UNTIL, today)).isTrue();
        assertThat(SleepRules.expired(SleepStatus.TEMPORARY, UNTIL, UNTIL)).isFalse();
        assertThat(SleepRules.expired(SleepStatus.FULL, UNTIL, today)).isFalse();
    }
}
