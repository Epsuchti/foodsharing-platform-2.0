package network.foodsharing.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A user following a whole forum: a bell for every new active thread. */
@Getter
@Setter
@Entity
@Table(name = "forum_follow")
public class ForumFollow extends BaseEntity {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "ambassador_forum", nullable = false)
    private boolean ambassadorForum;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
