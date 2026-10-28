package network.foodsharing.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;

import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A33 / decision 52: team chat = MEMBER (managers incl.), jumper chat = managers + JUMPER. */
class StoreConversationSyncTest {

    @ParameterizedTest(name = "{0} manager={1} → team={2}, jumper={3}")
    @CsvSource({
        "MEMBER, false, true, false",
        "MEMBER, true, true, true",
        "JUMPER, false, false, true",
        "APPLIED, false, false, false",
        "INVITED, false, false, false",
        ", false, false, false",
    })
    void statusMatrix(StoreMembershipStatus status, boolean manager, boolean team, boolean jumper) {
        assertThat(StoreConversationSync.belongsTo(StoreChat.TEAM, status, manager)).isEqualTo(team);
        assertThat(StoreConversationSync.belongsTo(StoreChat.JUMPER, status, manager)).isEqualTo(jumper);
    }
}
