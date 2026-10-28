package network.foodsharing.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import network.foodsharing.api.model.Role;
import network.foodsharing.poll.domain.PollScope;
import network.foodsharing.user.domain.User;
import org.junit.jupiter.api.Test;

/** Member scopes of upstream listActiveRegionMemberIds (AMBASSADORS is covered by PollFlowIT scope counts). */
class PollEligibilityServiceTest {

    static final UUID REGION = UUID.randomUUID();

    @Test
    void memberScopes() {
        User sharer = user(Role.FOODSHARER, true, REGION);
        User unverified = user(Role.FOODSAVER, false, REGION);
        User elsewhere = user(Role.FOODSAVER, true, UUID.randomUUID());
        User manager = user(Role.STORE_MANAGER, true, REGION);
        User ambassador = user(Role.AMBASSADOR, true, null);

        assertThat(PollEligibilityService.matches(sharer, PollScope.FOODSAVERS, REGION)).isFalse();
        assertThat(PollEligibilityService.matches(unverified, PollScope.FOODSAVERS, REGION)).isTrue();
        assertThat(PollEligibilityService.matches(unverified, PollScope.VERIFIED_FOODSAVERS, REGION)).isFalse();
        assertThat(PollEligibilityService.matches(elsewhere, PollScope.VERIFIED_FOODSAVERS, REGION)).isTrue();
        assertThat(PollEligibilityService.matches(elsewhere, PollScope.VERIFIED_FOODSAVERS_HOME_REGION, REGION))
                .isFalse();
        assertThat(PollEligibilityService.matches(manager, PollScope.VERIFIED_FOODSAVERS_HOME_REGION, REGION))
                .isTrue();
        assertThat(PollEligibilityService.matches(elsewhere, PollScope.STORE_MANAGERS, REGION)).isFalse();
        assertThat(PollEligibilityService.matches(manager, PollScope.STORE_MANAGERS, REGION)).isTrue();
        assertThat(PollEligibilityService.matches(ambassador, PollScope.STORE_MANAGERS, REGION)).isTrue();
    }

    private static User user(Role role, boolean verified, UUID homeRegion) {
        User user = new User();
        user.setRole(role);
        user.setVerified(verified);
        user.setHomeRegionId(homeRegion);
        return user;
    }
}
