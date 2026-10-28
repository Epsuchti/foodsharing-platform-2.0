package network.foodsharing.messaging.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    Optional<ConversationMessage> findByConversationIdAndSenderIdAndClientKey(UUID conversationId, UUID senderId,
                                                                             String clientKey);

    List<ConversationMessage> findByConversationIdOrderBySeqDesc(UUID conversationId, Pageable pageable);

    List<ConversationMessage> findByConversationIdAndSeqLessThanOrderBySeqDesc(UUID conversationId, long seq,
                                                                             Pageable pageable);

    List<ConversationMessage> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(UUID conversationId, long seq,
                                                                               Pageable pageable);
}
