package network.foodsharing.pickup.service;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.store.domain.UserTeamStatus;
import org.junit.jupiter.api.Test;

/** PU3 day chat (decision 165): team members of the store or people signed up that day. */
class DayChatRuleTest {

    @Test
    void teamMembersMayOpen() {
        assertThat(DayChatRule.mayOpen(UserTeamStatus.MEMBER, false)).isTrue();
        assertThat(DayChatRule.mayOpen(UserTeamStatus.COORDINATOR, false)).isTrue();
    }

    @Test
    void peopleSignedUpThatDayMayOpen() {
        assertThat(DayChatRule.mayOpen(UserTeamStatus.NO_MEMBER, true)).isTrue();
        assertThat(DayChatRule.mayOpen(UserTeamStatus.WAITING_LIST, true)).isTrue();
    }

    @Test
    void everybodyElseMayNot() {
        assertThat(DayChatRule.mayOpen(UserTeamStatus.NO_MEMBER, false)).isFalse();
        assertThat(DayChatRule.mayOpen(UserTeamStatus.WAITING_LIST, false)).isFalse();
        assertThat(DayChatRule.mayOpen(UserTeamStatus.APPLIED, false)).isFalse();
        assertThat(DayChatRule.mayOpen(UserTeamStatus.INVITED, false)).isFalse();
    }
}
