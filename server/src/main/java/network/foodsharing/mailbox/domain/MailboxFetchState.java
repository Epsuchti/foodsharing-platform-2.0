package network.foodsharing.mailbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Progress of an inbound source (IMAP: highest stored UID within {@link #uidValidity}; A35). */
@Getter
@Setter
@Entity
@Table(name = "mailbox_fetch_state")
public class MailboxFetchState extends BaseEntity {

    @Column(name = "source", nullable = false, length = 40, unique = true)
    private String source;

    @Column(name = "last_uid", nullable = false)
    private long lastUid;

    @Column(name = "uid_validity")
    private Long uidValidity;

    @Column(name = "last_fetch_at")
    private Instant lastFetchAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;
}
