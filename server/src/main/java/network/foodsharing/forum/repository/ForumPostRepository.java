package network.foodsharing.forum.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.forum.domain.ForumPost;
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
}
