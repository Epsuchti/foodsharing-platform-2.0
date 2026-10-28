package network.foodsharing.messaging.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.StoreConversation;
import network.foodsharing.store.service.TeamChatPort.StoreChat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreConversationRepository extends JpaRepository<StoreConversation, UUID> {

    Optional<StoreConversation> findByStoreIdAndKind(UUID storeId, StoreChat kind);

    Optional<StoreConversation> findByConversationId(UUID conversationId);

    List<StoreConversation> findByConversationIdIn(java.util.Collection<UUID> conversationIds);
}
