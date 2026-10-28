package network.foodsharing.forum.repository;

import java.util.UUID;
import network.foodsharing.forum.domain.ForumThread;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumThreadRepository extends JpaRepository<ForumThread, UUID> {

    /** Listing: sticky first, then latest post (inactive threads only for moderators or their authors). */
    @Query("""
            select t from ForumThread t where t.regionId = :regionId and t.ambassadorForum = :ambassadorForum
              and t.deletedAt is null and (t.active = true or :moderator = true or t.authorId = :userId)
            order by t.sticky desc, coalesce(t.lastPostAt, t.createdAt) desc, t.id""")
    Page<ForumThread> list(@Param("regionId") UUID regionId, @Param("ambassadorForum") boolean ambassadorForum,
                           @Param("moderator") boolean moderator, @Param("userId") UUID userId, Pageable pageable);

    /** Title (and body) search (ILIKE) in one forum, or every forum when {@code all} (ORGA). */
    @Query("""
            select t from ForumThread t where t.deletedAt is null
              and (t.active = true or :moderator = true)
              and (:all = true or (t.regionId = :regionId and t.ambassadorForum = :ambassadorForum))
              and (lower(t.title) like :pattern escape '\\'
                   or (:searchBody = true and exists (select p.id from ForumPost p where p.threadId = t.id
                       and p.deletedAt is null and (p.hiddenAt is null or :moderator = true)
                       and lower(p.body) like :pattern escape '\\')))
            order by coalesce(t.lastPostAt, t.createdAt) desc, t.id""")
    Page<ForumThread> search(@Param("pattern") String pattern, @Param("searchBody") boolean searchBody,
                             @Param("moderator") boolean moderator, @Param("all") boolean all,
                             @Param("regionId") UUID regionId, @Param("ambassadorForum") boolean ambassadorForum,
                             Pageable pageable);
}
