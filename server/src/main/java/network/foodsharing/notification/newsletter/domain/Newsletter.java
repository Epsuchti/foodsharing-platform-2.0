package network.foodsharing.notification.newsletter.domain;

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
import network.foodsharing.notification.domain.NewsletterStatus;

/** A newsletter (N7): global ({@code regionId} null, ORGA) or for a region subtree (region admins). */
@Getter
@Setter
@Entity
@Table(name = "newsletter")
public class Newsletter extends BaseEntity {

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body_markdown", nullable = false, length = 32600)
    private String bodyMarkdown;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsletterStatus status = NewsletterStatus.DRAFT;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;
}
