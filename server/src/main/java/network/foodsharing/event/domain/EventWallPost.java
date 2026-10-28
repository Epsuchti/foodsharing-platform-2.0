package network.foodsharing.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A comment on the event wall. */
@Getter
@Setter
@Entity
@Table(name = "event_wall_post")
public class EventWallPost extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "body", nullable = false, length = 4000)
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
