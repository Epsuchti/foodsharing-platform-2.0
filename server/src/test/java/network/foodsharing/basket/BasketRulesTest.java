package network.foodsharing.basket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.basket.service.BasketRules;
import org.junit.jupiter.api.Test;

/** Pure basket rules: open/expiry, who may request/delete, live requests, HOME rounding, distance. */
class BasketRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private final UUID owner = UUID.randomUUID();

    private Basket basket(BasketStatus status, Instant expiresAt, BasketLocationType type) {
        Basket basket = new Basket();
        basket.setOwnerId(owner);
        basket.setStatus(status);
        basket.setExpiresAt(expiresAt);
        basket.setLocationType(type);
        return basket;
    }

    @Test
    void openMeansActiveAndNotExpired() {
        assertThat(BasketRules.isOpen(basket(BasketStatus.ACTIVE, NOW.plusSeconds(1), BasketLocationType.HOME), NOW))
                .isTrue();
        assertThat(BasketRules.isOpen(basket(BasketStatus.ACTIVE, NOW, BasketLocationType.HOME), NOW)).isFalse();
        for (BasketStatus closed : new BasketStatus[] {BasketStatus.TAKEN, BasketStatus.EXPIRED, BasketStatus.DELETED}) {
            assertThat(BasketRules.isOpen(basket(closed, NOW.plus(Duration.ofDays(1)), BasketLocationType.HOME), NOW))
                    .as(closed.name()).isFalse();
        }
    }

    @Test
    void othersRequestOpenBasketsOwnerAndOrgaDelete() {
        Basket open = basket(BasketStatus.ACTIVE, NOW.plus(Duration.ofDays(1)), BasketLocationType.CUSTOM);
        UUID other = UUID.randomUUID();
        assertThat(BasketRules.mayRequest(open, other, NOW)).isTrue();
        assertThat(BasketRules.mayRequest(open, owner, NOW)).isFalse();
        assertThat(BasketRules.mayRequest(open, other, NOW.plus(Duration.ofDays(2)))).isFalse();
        assertThat(BasketRules.mayDelete(open, owner, false)).isTrue();
        assertThat(BasketRules.mayDelete(open, other, false)).isFalse();
        assertThat(BasketRules.mayDelete(open, other, true)).isTrue();
    }

    @Test
    void onlyWithdrawnRequestsAreNotLive() {
        for (BasketRequestStatus status : BasketRequestStatus.values()) {
            assertThat(BasketRules.isLive(status)).as(status.name()).isEqualTo(status != BasketRequestStatus.WITHDRAWN);
        }
    }

    @Test
    void homeCoordinatesAreRoundedForOthersOnly() {
        Basket home = basket(BasketStatus.ACTIVE, NOW, BasketLocationType.HOME);
        Basket custom = basket(BasketStatus.ACTIVE, NOW, BasketLocationType.CUSTOM);
        assertThat(BasketRules.publicCoordinate(home, 47.389123, false)).isEqualTo(47.389);
        assertThat(BasketRules.publicCoordinate(home, 47.389123, true)).isEqualTo(47.389123);
        assertThat(BasketRules.publicCoordinate(custom, 47.389123, false)).isEqualTo(47.389123);
    }

    @Test
    void distanceZurichToWinterthur() {
        assertThat(BasketRules.distanceKm(47.3769, 8.5417, 47.4988, 8.7237)).isCloseTo(19.0, within(1.5));
        assertThat(BasketRules.distanceKm(47.0, 8.0, 47.0, 8.0)).isZero();
    }
}
