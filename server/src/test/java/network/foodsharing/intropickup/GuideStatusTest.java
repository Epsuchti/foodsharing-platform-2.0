package network.foodsharing.intropickup;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.intropickup.domain.GuideStatus;
import org.junit.jupiter.api.Test;

/** EA17: guide list status (active, paused in sleep mode, disabled when the right is gone or the account inactive). */
class GuideStatusTest {

    @Test
    void statusFollowsRightAccountAndSleepMode() {
        assertThat(GuideStatus.of(true, true, false)).isEqualTo(GuideStatus.ACTIVE);
        assertThat(GuideStatus.of(true, true, true)).isEqualTo(GuideStatus.PAUSED);
        assertThat(GuideStatus.of(true, false, false)).isEqualTo(GuideStatus.DISABLED);
        assertThat(GuideStatus.of(false, true, false)).isEqualTo(GuideStatus.DISABLED);
        assertThat(GuideStatus.of(false, false, true)).isEqualTo(GuideStatus.DISABLED);
    }
}
