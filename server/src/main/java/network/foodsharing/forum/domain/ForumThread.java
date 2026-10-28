package network.foodsharing.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A thread in a region forum or its ambassador forum (upstream fs_theme). */
@Getter
@Setter
@Entity
@Table(name = "forum_thread")
public class ForumThread extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "ambassador_forum", nullable = false)
    private boolean ambassadorForum;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ThreadStatus status = ThreadStatus.OPEN;

    @Column(name = "sticky", nullable = false)
    private boolean sticky;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "post_count", nullable = false)
    private int postCount;

    @Column(name = "last_post_at")
    private Instant lastPostAt;

    @Column(name = "last_post_author_id")
    private UUID lastPostAuthorId;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
