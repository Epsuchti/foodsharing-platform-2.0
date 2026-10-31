package network.foodsharing.event.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.event.domain.EventInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventInvitationRepository extends JpaRepository<EventInvitation, UUID> {

    List<EventInvitation> findByEventIdOrderByCreatedAtAscIdAsc(UUID eventId);

    List<EventInvitation> findByEventIdIn(Collection<UUID> eventIds);

    Optional<EventInvitation> findByEventIdAndUserId(UUID eventId, UUID userId);
}
