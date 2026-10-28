package network.foodsharing.messaging.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.history.NoHistory;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.domain.StoreConversation;
import network.foodsharing.messaging.repository.StoreConversationRepository;
import network.foodsharing.store.domain.StoreMembership;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.domain.StoreTeamChangedEvent;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store team and jumper chats follow the team (A33, decision 52): TEAM = MEMBER (managers included), JUMPER =
 * managers + JUMPER. Chats are created on first use and synced on every {@link StoreTeamChangedEvent} (same
 * transaction) and by the hourly {@code conversation-sync} job.
 */
@Service
public class StoreConversationSync {

    private final StoreConversationRepository links;
    private final MessagingService messaging;
    private final StoreService stores;

    public StoreConversationSync(StoreConversationRepository links, MessagingService messaging, StoreService stores) {
        this.links = links;
        this.messaging = messaging;
        this.stores = stores;
    }

    /** Who belongs to a store chat (pure rule, unit tested). */
    static boolean belongsTo(StoreChat chat, StoreMembershipStatus status, boolean manager) {
        if (status == null) {
            return false;
        }
        return switch (chat) {
            case TEAM -> status == StoreMembershipStatus.MEMBER;
            case JUMPER -> status == StoreMembershipStatus.JUMPER || (status == StoreMembershipStatus.MEMBER && manager);
        };
    }

    @EventListener
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public void on(StoreTeamChangedEvent event) {
        syncStore(event.storeId());
    }

    /** The chat id, created and synced on first use. */
    @Transactional
    @NoHistory(reason = "createLocked/syncMembers record CONVERSATION_CREATED/MEMBERS_SYNCED")
    public UUID ensure(UUID storeId, StoreChat chat) {
        Optional<StoreConversation> existing = links.findByStoreIdAndKind(storeId, chat);
        if (existing.isPresent()) {
            return existing.get().getConversationId();
        }
        StoreConversation link = new StoreConversation();
        link.setStoreId(storeId);
        link.setKind(chat);
        link.setConversationId(messaging.createLocked(chat == StoreChat.TEAM
                ? ConversationKind.STORE_TEAM : ConversationKind.STORE_JUMPER).getId());
        links.save(link);
        messaging.syncMembers(link.getConversationId(), wanted(storeId, chat));
        return link.getConversationId();
    }

    /** Reconciles both chats of a store (created when missing). */
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public int syncStore(UUID storeId) {
        int changes = 0;
        for (StoreChat chat : StoreChat.values()) {
            UUID conversationId = ensure(storeId, chat);
            changes += messaging.syncMembers(conversationId, wanted(storeId, chat));
        }
        return changes;
    }

    /** Reconciles every existing store chat (job). */
    @Transactional
    @NoHistory(reason = "syncMembers records MEMBERS_SYNCED")
    public int syncAll() {
        Set<UUID> storeIds = new HashSet<>();
        links.findAll().forEach(link -> storeIds.add(link.getStoreId()));
        int changes = 0;
        for (UUID storeId : storeIds) {
            changes += syncStore(storeId);
        }
        return changes;
    }

    private Set<UUID> wanted(UUID storeId, StoreChat chat) {
        Set<UUID> wanted = new HashSet<>();
        for (StoreMembership m : stores.teamMemberships(storeId)) {
            if (belongsTo(chat, m.getStatus(), m.isManager())) {
                wanted.add(m.getUserId());
            }
        }
        return wanted;
    }
}
