package network.foodsharing.forum.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumPost;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {

    List<ForumPost> findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID threadId);

    Optional<ForumPost> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ForumPost> findFirstByThreadIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID threadId);

    long countByThreadIdAndDeletedAtIsNull(UUID threadId);

    long countByThreadIdAndDeletedAtIsNullAndHiddenAtIsNull(UUID threadId);

    /** First matching post body of a thread (search snippet). */
    @Query("""
            select p from ForumPost p where p.threadId = :threadId and p.deletedAt is null
              and (p.hiddenAt is null or :moderator = true) and lower(p.body) like :pattern escape '\\'
            order by p.createdAt""")
    List<ForumPost> matching(@Param("threadId") UUID threadId, @Param("pattern") String pattern,
                             @Param("moderator") boolean moderator);

    /**
     * Activity feed: visible posts of active threads in the given region forums ({@code [ForumPost, ForumThread]}),
     * newest first.
     */
    @Query("""
            select p, t from ForumPost p, ForumThread t where p.threadId = t.id and p.deletedAt is null
              and p.hiddenAt is null and t.deletedAt is null and t.active = true and p.createdAt < :before
              and ((t.ambassadorForum = false and t.regionId in :memberRegions)
                   or (t.ambassadorForum = true and t.regionId in :adminRegions))
            order by p.createdAt desc, p.id""")
    List<Object[]> feed(@Param("memberRegions") Collection<UUID> memberRegions,
                        @Param("adminRegions") Collection<UUID> adminRegions, @Param("before") Instant before,
                        Pageable pageable);
}
