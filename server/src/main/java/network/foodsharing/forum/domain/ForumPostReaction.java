package network.foodsharing.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An emoji reaction of a user on a post (upstream fs_post_reaction). */
@Getter
@Setter
@Entity
@Table(name = "forum_post_reaction")
public class ForumPostReaction extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reaction_key", nullable = false, length = 40)
    private ReactionKey reactionKey;
}
