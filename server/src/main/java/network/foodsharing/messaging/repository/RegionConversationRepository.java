package network.foodsharing.messaging.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.messaging.domain.RegionConversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionConversationRepository extends JpaRepository<RegionConversation, UUID> {

    Optional<RegionConversation> findByRegionId(UUID regionId);

    Optional<RegionConversation> findByConversationId(UUID conversationId);

    List<RegionConversation> findByConversationIdIn(java.util.Collection<UUID> conversationIds);
}
