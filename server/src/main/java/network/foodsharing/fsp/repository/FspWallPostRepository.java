package network.foodsharing.fsp.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.fsp.domain.FspWallPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FspWallPostRepository extends JpaRepository<FspWallPost, UUID> {

    Page<FspWallPost> findByFspIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID fspId, Pageable pageable);

    /** Activity feed (A19): newest posts of the given FSPs before a cursor. */
    List<FspWallPost> findByFspIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDescIdDesc(Collection<UUID> fspIds,
                                                                                           Instant before,
                                                                                           Pageable pageable);

    List<FspWallPost> findByAuthorId(UUID authorId);
}
