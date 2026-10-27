package network.foodsharing.verification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import network.foodsharing.config.AppProperties;
import org.junit.jupiter.api.Test;

/** Passport validity: three years from the start, valid while today is before the end (leap day included). */
class PassportValidityTest {

    private final PassportService service = new PassportService(null, null, null, null, null, null, null, null, null,
            null, null, null, new AppProperties(), null);

    @Test
    void threeYearsFromTheStart() {
        assertThat(service.until(LocalDate.of(2025, 3, 1))).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(service.until(LocalDate.of(2024, 2, 29))).isEqualTo(LocalDate.of(2027, 2, 28));
    }

    @Test
    void validUntilTheLastDayExclusive() {
        var validity = new PassportService.Validity(LocalDate.of(2023, 11, 2), LocalDate.of(2026, 11, 2));
        assertThat(PassportService.isValid(validity, LocalDate.of(2026, 11, 1))).isTrue();
        assertThat(PassportService.isValid(validity, LocalDate.of(2026, 11, 2))).isFalse();
        assertThat(PassportService.isValid(null, LocalDate.of(2026, 11, 1))).isFalse();
    }
}
