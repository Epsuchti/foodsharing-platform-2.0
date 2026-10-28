package network.foodsharing.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Aggregated votes for one value of one option (upstream fs_poll_option_has_value). */
@Getter
@Setter
@Entity
@Table(name = "poll_option_value")
public class PollOptionValue extends BaseEntity {

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(name = "option_value", nullable = false)
    private int value;

    @Column(name = "votes", nullable = false)
    private int votes;
}
