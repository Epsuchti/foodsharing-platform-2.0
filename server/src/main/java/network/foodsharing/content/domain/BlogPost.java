package network.foodsharing.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A blog post (global or of a region); markdown body, public once published (upstream Blogpost). */
@Getter
@Setter
@Entity
@Table(name = "blog_post")
public class BlogPost extends BaseEntity {

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "teaser", nullable = false, length = 2000)
    private String teaser;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;

    @Column(name = "picture_upload_id")
    private UUID pictureUploadId;

    @Column(name = "published", nullable = false)
    private boolean published;

    /** An ambassador asked to publish (upstream "new check"); an editor publishes. */
    @Column(name = "awaiting_check", nullable = false)
    private boolean awaitingCheck;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
