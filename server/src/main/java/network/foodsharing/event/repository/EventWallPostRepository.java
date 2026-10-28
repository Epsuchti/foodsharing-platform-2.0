package network.foodsharing.event.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.event.domain.EventWallPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventWallPostRepository extends JpaRepository<EventWallPost, UUID> {

    List<EventWallPost> findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID eventId);
}
