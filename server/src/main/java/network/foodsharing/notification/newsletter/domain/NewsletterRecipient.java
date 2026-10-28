package network.foodsharing.notification.newsletter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One recipient of a queued newsletter; QUEUED once its mail is in the outbox. */
@Getter
@Setter
@Entity
@Table(name = "newsletter_recipient")
public class NewsletterRecipient extends BaseEntity {

    public enum Status { PENDING, QUEUED, FAILED }

    @Column(name = "newsletter_id", nullable = false)
    private UUID newsletterId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "mail_outbox_id")
    private UUID mailOutboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;
}
