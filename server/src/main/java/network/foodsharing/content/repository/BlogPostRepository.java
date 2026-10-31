package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.content.domain.BlogPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    @Query("select b from BlogPost b where b.published = true and b.deletedAt is null"
            + " order by b.publishedAt desc, b.createdAt desc, b.id desc")
    Page<BlogPost> findPublished(Pageable pageable);

    @Query("select b from BlogPost b where b.published = true and b.deletedAt is null and b.regionId = :regionId"
            + " order by b.publishedAt desc, b.createdAt desc, b.id desc")
    Page<BlogPost> findPublishedOfRegion(@Param("regionId") UUID regionId, Pageable pageable);

    @Query("select b from BlogPost b where b.deletedAt is null order by b.createdAt desc, b.id desc")
    Page<BlogPost> findAllActive(Pageable pageable);

    @Query("select b from BlogPost b where b.deletedAt is null and (b.authorId = :userId or b.regionId in :regionIds)"
            + " order by b.createdAt desc, b.id desc")
    Page<BlogPost> findActiveOfAuthorOrRegions(@Param("userId") UUID userId,
                                               @Param("regionIds") Collection<UUID> regionIds, Pageable pageable);

    List<BlogPost> findByAuthorId(UUID authorId);
}
