package network.foodsharing.messaging.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.ConversationMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

    List<ConversationMember> findByConversationId(UUID conversationId);

    List<ConversationMember> findByConversationIdIn(Collection<UUID> conversationIds);

    Optional<ConversationMember> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);

    List<ConversationMember> findByUserId(UUID userId);

    /** My memberships, latest conversation activity first (conversations without messages last). */
    @Query(value = "select m from ConversationMember m, Conversation c where c.id = m.conversationId and m.userId = :userId"
            + " order by case when c.lastMessageAt is null then 1 else 0 end, c.lastMessageAt desc, c.createdAt desc",
            countQuery = "select count(m) from ConversationMember m where m.userId = :userId")
    Page<ConversationMember> pageOf(@Param("userId") UUID userId, Pageable pageable);

    /** Conversations of the user where exactly these members are present (DIRECT/GROUP lookup candidates). */
    @Query("select m.conversationId from ConversationMember m where m.userId = :userId")
    List<UUID> conversationIdsOf(@Param("userId") UUID userId);
}
