package network.foodsharing.notification.mail.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.api.model.Language;
import network.foodsharing.common.persistence.BaseEntity;

/** A rendered mail waiting in (or done with) the outbox ({@code mail_outbox}). */
@Getter
@Setter
@Entity
@Table(name = "mail_outbox")
public class MailOutboxMessage extends BaseEntity {

    public static final int MAX_BODY_LENGTH = 32600;

    @Column(name = "to_address", nullable = false, length = 320)
    private String toAddress;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "text_body", nullable = false, length = MAX_BODY_LENGTH)
    private String textBody;

    @Column(name = "html_body", length = MAX_BODY_LENGTH)
    private String htmlBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false, length = 5)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MailStatus status = MailStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    /** Extra MIME headers, one {@code Name: value} per line (List-Unsubscribe). */
    @Column(name = "headers", length = 4000)
    private String headers;

    /** Sender override (mailbox mails, A35); null = app.mail.from. */
    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "reply_to", length = 320)
    private String replyTo;

    /** Upload ids of the attachments as a JSON string array (mailbox mails). */
    @Column(name = "attachments_json", length = 4000)
    private String attachmentsJson;

    @Column(name = "template", nullable = false, length = 100)
    private String template;

    /** Recipient user when known (A27). */
    @Column(name = "user_id")
    private java.util.UUID userId;

    /** NotificationCategory name, NEWSLETTER or AUTH (A27). */
    @Column(name = "category", length = 40)
    private String category;

    /** Higher first ({@link #PRIORITY_AUTH} for sign-in mails). */
    @Column(name = "priority", nullable = false)
    private int priority;

    /** Set when the rate limiter deferred this mail (A27). */
    @Column(name = "rate_limited_until")
    private Instant rateLimitedUntil;

    /** Notification identifier for duplicate suppression. */
    @Column(name = "identifier", length = 200)
    private String identifier;

    public static final int PRIORITY_AUTH = 10;
}
