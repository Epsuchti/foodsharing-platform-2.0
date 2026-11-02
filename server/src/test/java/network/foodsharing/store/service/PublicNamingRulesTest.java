package network.foodsharing.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.store.domain.PublicityStatus;
import org.junit.jupiter.api.Test;

/** Decision 153, S17 D6. */
class PublicNamingRulesTest {

    private static ApiErrorCode code(Runnable call) {
        try {
            call.run();
            return null;
        } catch (ApiException e) {
            return e.getCode();
        }
    }

    @Test
    void orgaAndManagersMayChangeIt() {
        assertThat(PublicNamingRules.mayChange(true, false)).isTrue();
        assertThat(PublicNamingRules.mayChange(false, true)).isTrue();
        assertThat(PublicNamingRules.mayChange(false, false)).isFalse();
    }

    @Test
    void goesOnOnlyWithPublicityAllowed() {
        assertThat(PublicNamingRules.resolve(false, true, PublicityStatus.ALLOWED, true)).isTrue();
        for (PublicityStatus other : PublicityStatus.values()) {
            if (other != PublicityStatus.ALLOWED) {
                assertThat(code(() -> PublicNamingRules.resolve(false, true, other, true)))
                        .isEqualTo(ApiErrorCode.PUBLICITY_NOT_ALLOWED);
            }
        }
    }

    @Test
    void goesOff() {
        assertThat(PublicNamingRules.resolve(true, false, PublicityStatus.ALLOWED, true)).isFalse();
    }

    @Test
    void leavingAllowedClearsItForAnyoneWhoMayEdit() {
        for (PublicityStatus other : PublicityStatus.values()) {
            if (other != PublicityStatus.ALLOWED) {
                assertThat(PublicNamingRules.resolve(true, null, other, false)).isFalse();
                assertThat(PublicNamingRules.resolve(true, true, other, false)).isFalse();
                assertThat(PublicNamingRules.resolve(true, false, other, false)).isFalse();
            }
        }
    }

    @Test
    void othersMayNotChangeItButMaySendTheSameValue() {
        assertThatThrownBy(() -> PublicNamingRules.resolve(false, true, PublicityStatus.ALLOWED, false))
                .isInstanceOf(ApiException.class);
        assertThat(code(() -> PublicNamingRules.resolve(true, false, PublicityStatus.ALLOWED, false)))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(PublicNamingRules.resolve(true, true, PublicityStatus.ALLOWED, false)).isTrue();
        assertThat(PublicNamingRules.resolve(false, null, PublicityStatus.ALLOWED, false)).isFalse();
    }
}
