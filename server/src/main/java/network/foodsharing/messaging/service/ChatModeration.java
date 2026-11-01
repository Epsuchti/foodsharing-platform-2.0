package network.foodsharing.messaging.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.Conversation;
import network.foodsharing.messaging.domain.ConversationKind;
import network.foodsharing.messaging.repository.ConversationRepository;
import network.foodsharing.messaging.repository.RegionConversationRepository;
import network.foodsharing.messaging.repository.StoreConversationRepository;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moderators of a conversation (A33, resume item 16): they may delete other people's messages with a reason and read
 * the original text of edited and deleted messages in the history.
 * <ul>
 *   <li>ORGA in every conversation;</li>
 *   <li>STORE_TEAM / STORE_JUMPER: whoever may edit the linked store ({@code mayEditStore});</li>
 *   <li>REGION: whoever may administrate the linked region ({@code mayAdministrateRegion});</li>
 *   <li>DIRECT / GROUP: ORGA only.</li>
 * </ul>
 */
@Component
public class ChatModeration {

    private final ConversationRepository conversations;
    private final StoreConversationRepository storeConversations;
    private final RegionConversationRepository regionConversations;
    private final StoreService stores;
    private final StorePermissionService storeRights;
    private final PermissionService permissions;

    public ChatModeration(ConversationRepository conversations, StoreConversationRepository storeConversations,
                          RegionConversationRepository regionConversations, StoreService stores,
                          StorePermissionService storeRights, PermissionService permissions) {
        this.conversations = conversations;
        this.storeConversations = storeConversations;
        this.regionConversations = regionConversations;
        this.stores = stores;
        this.storeRights = storeRights;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public boolean isModerator(SessionPrincipal actor, UUID conversationId) {
        return conversations.findById(conversationId).map(c -> isModerator(actor, c)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isModerator(SessionPrincipal actor, Conversation conversation) {
        if (actor == null || conversation == null) {
            return false;
        }
        if (permissions.isOrga(actor)) {
            return true;
        }
        ConversationKind kind = conversation.getKind();
        if (kind == ConversationKind.STORE_TEAM || kind == ConversationKind.STORE_JUMPER) {
            return storeConversations.findByConversationId(conversation.getId())
                    .flatMap(link -> store(link.getStoreId()))
                    .map(store -> storeRights.rights(actor, store).mayEditStore()).orElse(false);
        }
        if (kind == ConversationKind.REGION) {
            return regionConversations.findByConversationId(conversation.getId())
                    .map(link -> permissions.mayAdministrateRegion(actor, link.getRegionId())).orElse(false);
        }
        return false;
    }

    /** The live store (no exception: a thrown one would mark the caller's transaction rollback-only). */
    private Optional<Store> store(UUID storeId) {
        return stores.storesById(List.of(storeId)).stream().filter(store -> store.getDeletedAt() == null).findFirst();
    }
}
