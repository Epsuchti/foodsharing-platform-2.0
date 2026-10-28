package network.foodsharing.automation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Request distance rule, holiday parsing and link message placeholders (M4 B4). */
class AutomationRulesTest {

    @Test
    void haversineDistanceAndMissingCoordinates() {
        // Zürich HB → Bern Bahnhof ≈ 95 km
        double km = RequestPolicyRunner.distanceKm(47.3779, 8.5403, 46.9490, 7.4392);
        assertThat(km).isBetween(94.0, 97.0);
        assertThat(RequestPolicyRunner.distanceKm(null, 8.5, 47.0, 7.4)).isNull();
        assertThat(RequestPolicyRunner.formatDistance(12.345)).isEqualTo("12.3");
    }

    @Test
    void nagerRowsAreNationalOrCantonal() {
        var rows = JsonMapper.builder().build().readTree("""
                [{"date":"2026-01-01","localName":"Neujahr","global":true,"counties":null},
                 {"date":"2026-01-02","localName":"Berchtoldstag","global":false,"counties":["CH-ZH","CH-BE"]},
                 {"date":"2026-05-01","localName":"Tag der Arbeit","global":false,"counties":["CH-BS"]},
                 {"date":"2026-08-01","name":"Bundesfeier","nationalHoliday":true}]""");
        List<HolidayCalendarService.Holiday> holidays = HolidayCalendarService.parse(rows, "CH");
        assertThat(holidays).containsExactly(
                new HolidayCalendarService.Holiday(LocalDate.parse("2026-01-01"), "Neujahr", null),
                new HolidayCalendarService.Holiday(LocalDate.parse("2026-01-02"), "Berchtoldstag", "ZH"),
                new HolidayCalendarService.Holiday(LocalDate.parse("2026-01-02"), "Berchtoldstag", "BE"),
                new HolidayCalendarService.Holiday(LocalDate.parse("2026-05-01"), "Tag der Arbeit", "BS"),
                new HolidayCalendarService.Holiday(LocalDate.parse("2026-08-01"), "Bundesfeier", null));
    }

    @Test
    void cantonCodesCoverAllCantons() {
        assertThat(HolidayCalendarService.CANTONS).hasSize(26).containsEntry("zuerich", "ZH");
    }

    @Test
    void linkMessagePlaceholdersAreChecked() {
        assertThatCode(() -> PickupPolicyService.validateMessage(
                "{{name}} {{ storeName }} {{linkedStoreName}} {{pickupDate}} {{requirement}} {{graceMinutes}}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> PickupPolicyService.validateMessage(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PickupPolicyService.validateMessage("Hallo {{vorname}}"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ApiErrorCode.INVALID_TEMPLATE_VARIABLE));
    }
}
