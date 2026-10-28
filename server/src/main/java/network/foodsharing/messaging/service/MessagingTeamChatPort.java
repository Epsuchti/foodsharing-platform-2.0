package network.foodsharing.messaging.service;

import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.store.service.TeamChatPort;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The real {@link TeamChatPort} (A33, decision 48): system messages in store chats, direct messages. */
@Primary
@Service
public class MessagingTeamChatPort implements TeamChatPort {

    private final StoreConversationSync stores;
    private final MessagingService messaging;

    public MessagingTeamChatPort(StoreConversationSync stores, MessagingService messaging) {
        this.stores = stores;
        this.messaging = messaging;
    }

    @Override
    @Transactional
    @NoHistory(reason = "postSystem records SYSTEM_MESSAGE_POSTED")
    public void postToTeamChat(UUID storeId, StoreChat chat, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        messaging.postSystem(stores.ensure(storeId, chat), text);
    }

    @Override
    @Transactional
    @NoHistory(reason = "lookup records CONVERSATION_CREATED; messages are content")
    public void sendDirect(UUID fromUserId, UUID toUserId, String text) {
        if (text == null || text.isBlank() || fromUserId.equals(toUserId)) {
            return;
        }
        messaging.sendDirect(fromUserId, toUserId, text);
    }
}
