package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import org.junit.jupiter.api.Test;

/**
 * PU1 prefetch horizon of the pickups list (e2e pu1-regular): without a range a store lists its pickups from now to
 * now + its prefetch days (S-03: 7, S-01: 14), so regular pickups beyond the horizon are not offered yet.
 */
class PickupListRangeTest {

    static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");

    @Test
    void defaultRangeEndsAtThePrefetchHorizon() {
        var week = PickupScheduleService.listRange(NOW, null, null, 7);
        assertThat(week.start()).isEqualTo(NOW);
        assertThat(week.end()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(PickupScheduleService.listRange(NOW, null, null, 14).end()).isEqualTo(NOW.plus(Duration.ofDays(14)));
    }

    @Test
    void anExplicitRangeWinsAndMustNotBeEmpty() {
        Instant from = NOW.plus(Duration.ofDays(20));
        Instant to = NOW.plus(Duration.ofDays(30));
        var range = PickupScheduleService.listRange(NOW, from, to, 7);
        assertThat(range.start()).isEqualTo(from);
        assertThat(range.end()).isEqualTo(to);
        assertThat(PickupScheduleService.listRange(NOW, from, null, 30).end()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThatThrownBy(() -> PickupScheduleService.listRange(NOW, to, from, 7)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> PickupScheduleService.listRange(NOW, from, from, 7)).isInstanceOf(ApiException.class);
    }
}
