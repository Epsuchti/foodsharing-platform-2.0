package network.foodsharing.event.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.event.domain.EventWallPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventWallPostRepository extends JpaRepository<EventWallPost, UUID> {

    List<EventWallPost> findByEventIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID eventId);

    /** Activity feed (A19): newest live posts of the given owners before a cursor. */
    List<EventWallPost> findByEventIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDesc(
            Collection<UUID> ids, Instant before, Pageable pageable);
}
