package network.foodsharing.poll.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One answer option (upstream fs_poll_has_options). */
@Getter
@Setter
@Entity
@Table(name = "poll_option")
public class PollOption extends BaseEntity {

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(name = "option_text", nullable = false, length = 500)
    private String text;
}
