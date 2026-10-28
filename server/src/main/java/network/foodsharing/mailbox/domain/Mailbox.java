package network.foodsharing.mailbox.domain;

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

/** A mailbox (A35): address {@code <emailName>@app.mailbox.domain}, owned by a region/group or a user (MB6). */
@Getter
@Setter
@Entity
@Table(name = "mailbox")
public class Mailbox extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "email_name", nullable = false, length = 64, unique = true)
    private String emailName;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private MailboxOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "last_access_at")
    private Instant lastAccessAt;
}
