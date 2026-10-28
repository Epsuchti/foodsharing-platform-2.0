package network.foodsharing.mailbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A configured reader of a mailbox besides the ambassadors of the owning region (A35). */
@Getter
@Setter
@Entity
@Table(name = "mailbox_member")
public class MailboxMember extends BaseEntity {

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
}
