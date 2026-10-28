package network.foodsharing.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Who voted when - never what (upstream fs_foodsaver_has_poll). */
@Getter
@Setter
@Entity
@Table(name = "poll_vote")
public class PollVote extends BaseEntity {

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;
}
