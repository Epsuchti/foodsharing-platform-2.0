package network.foodsharing.notification.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BellRepository extends JpaRepository<Bell, UUID> {

    List<Bell> findByIdentifierAndDeletedAtIsNull(String identifier);

    boolean existsByIdentifierAndCreatedAtAfter(String identifier, Instant since);

    @Query("select b from Bell b where (:userId is null or b.userId = :userId) and (:type is null or b.type = :type) "
            + "order by b.createdAt desc, b.id")
    List<Bell> search(@Param("userId") UUID userId, @Param("type") NotificationType type);

    /** Own visible bells, unread first, newest first (index ix_bell_user_id_read_at_created_at). */
    @Query(value = "select b from Bell b where b.userId = :userId and b.deletedAt is null "
            + "and (:unreadOnly = false or b.readAt is null) "
            + "order by case when b.readAt is null then 0 else 1 end, b.createdAt desc, b.id",
            countQuery = "select count(b) from Bell b where b.userId = :userId and b.deletedAt is null "
                    + "and (:unreadOnly = false or b.readAt is null)")
    Page<Bell> page(@Param("userId") UUID userId, @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    long countByUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID userId);

    Optional<Bell> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Bell b set b.readAt = :now where b.userId = :userId and b.readAt is null and b.deletedAt is null "
            + "and b.id in :ids")
    int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Bell b set b.readAt = :now where b.userId = :userId and b.readAt is null and b.deletedAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Bell b set b.deletedAt = :now where b.userId = :userId and b.deletedAt is null "
            + "and (:readOnly = false or b.readAt is not null)")
    int deleteAll(@Param("userId") UUID userId, @Param("readOnly") boolean readOnly, @Param("now") Instant now);

    /** Cleanup (§6.3): bells read before {@code readBefore} or deleted before {@code deletedBefore}. */
    @Modifying
    @Query("delete from Bell b where b.readAt < :readBefore or b.deletedAt < :deletedBefore")
    int purge(@Param("readBefore") Instant readBefore, @Param("deletedBefore") Instant deletedBefore);
}
