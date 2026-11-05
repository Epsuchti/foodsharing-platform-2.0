package network.foodsharing.messaging.service;

import java.util.Collection;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.store.service.DayChatPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The {@link DayChatPort}: day chats of a store are plain named GROUP conversations (decision 165). */
@Service
public class MessagingDayChatPort implements DayChatPort {

    private final MessagingService messaging;

    public MessagingDayChatPort(MessagingService messaging) {
        this.messaging = messaging;
    }

    @Override
    @Transactional
    @NoHistory(reason = "createGroup records CONVERSATION_CREATED")
    public UUID createGroup(UUID actorId, Collection<UUID> memberIds, String name) {
        return messaging.createGroup(actorId, memberIds, name).getId();
    }

    @Override
    @Transactional
    @NoHistory(reason = "addMissingMembers records MEMBER_ADDED per added member")
    public int addMissingMembers(UUID conversationId, Collection<UUID> userIds) {
        return messaging.addMissingMembers(conversationId, userIds);
    }

    @Override
    @Transactional(readOnly = true)
    public String name(UUID conversationId) {
        return messaging.nameOf(conversationId);
    }
}
