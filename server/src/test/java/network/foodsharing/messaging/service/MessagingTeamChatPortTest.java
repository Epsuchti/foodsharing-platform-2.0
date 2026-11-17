package network.foodsharing.messaging.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import org.junit.jupiter.api.Test;

/**
 * S10/AU4: automation and team messages reach the store chats as system messages (no sender) in the conversation the
 * store sync ensures; direct messages of the store module go person to person, never to oneself or empty.
 */
class MessagingTeamChatPortTest {

    private final StoreConversationSync stores = mock(StoreConversationSync.class);
    private final MessagingService messaging = mock(MessagingService.class);
    private final MessagingTeamChatPort port = new MessagingTeamChatPort(stores, messaging);

    @Test
    void teamChatPostsAreSystemMessagesInTheEnsuredStoreChat() {
        UUID store = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UUID jumpers = UUID.randomUUID();
        when(stores.ensure(store, StoreChat.TEAM)).thenReturn(team);
        when(stores.ensure(store, StoreChat.JUMPER)).thenReturn(jumpers);

        port.postToTeamChat(store, StoreChat.TEAM, "Morgen ist ein Platz frei");
        port.postToTeamChat(store, StoreChat.JUMPER, "Springer gesucht");

        verify(messaging).postSystem(team, "Morgen ist ein Platz frei");
        verify(messaging).postSystem(jumpers, "Springer gesucht");
    }

    @Test
    void blankTeamChatPostsAreDropped() {
        port.postToTeamChat(UUID.randomUUID(), StoreChat.TEAM, "  ");
        port.postToTeamChat(UUID.randomUUID(), StoreChat.TEAM, null);
        verifyNoInteractions(stores, messaging);
    }

    @Test
    void directMessagesGoFromPersonToPersonButNeverToOneselfOrEmpty() {
        UUID manager = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        port.sendDirect(manager, member, "Bitte pausiere ein paar Wochen.");
        verify(messaging).sendDirect(manager, member, "Bitte pausiere ein paar Wochen.");

        MessagingService other = mock(MessagingService.class);
        MessagingTeamChatPort quiet = new MessagingTeamChatPort(stores, other);
        quiet.sendDirect(manager, manager, "an mich");
        quiet.sendDirect(manager, member, " ");
        quiet.sendDirect(manager, member, null);
        verify(other, org.mockito.Mockito.never()).sendDirect(any(), any(), any());
    }
}
