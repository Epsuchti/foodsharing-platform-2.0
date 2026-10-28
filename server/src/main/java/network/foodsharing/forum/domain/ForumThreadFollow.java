package network.foodsharing.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A user following a thread by bell and/or mail (upstream fs_theme_follower). */
@Getter
@Setter
@Entity
@Table(name = "forum_thread_follow")
public class ForumThreadFollow extends BaseEntity {

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "bell", nullable = false)
    private boolean bell;

    @Column(name = "mail", nullable = false)
    private boolean mail;
}
