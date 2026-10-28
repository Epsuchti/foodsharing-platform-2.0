package network.foodsharing.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A post in a forum thread (upstream fs_theme_post); the body is raw markdown. */
@Getter
@Setter
@Entity
@Table(name = "forum_post")
public class ForumPost extends BaseEntity {

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by")
    private UUID hiddenBy;

    @Column(name = "hidden_reason", length = 500)
    private String hiddenReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isHidden() {
        return hiddenAt != null;
    }
}
