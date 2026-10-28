package network.foodsharing.store.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.store.domain.StoreWallPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreWallPostRepository extends JpaRepository<StoreWallPost, UUID> {

    Page<StoreWallPost> findByStoreIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID storeId, Pageable pageable);

    Optional<StoreWallPost> findByIdAndStoreIdAndDeletedAtIsNull(UUID id, UUID storeId);

    List<StoreWallPost> findByStoreIdAndDeletedAtIsNull(UUID storeId);

    /** Activity feed (A19): newest live posts of the given owners before a cursor. */
    List<StoreWallPost> findByStoreIdInAndDeletedAtIsNullAndCreatedAtBeforeOrderByCreatedAtDesc(
            Collection<UUID> ids, Instant before, Pageable pageable);
}
