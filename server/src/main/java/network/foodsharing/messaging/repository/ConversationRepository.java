package network.foodsharing.messaging.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Row lock for assigning the next message seq (A33). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") UUID id);
}
