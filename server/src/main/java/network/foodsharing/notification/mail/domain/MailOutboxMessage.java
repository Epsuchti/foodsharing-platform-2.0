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

    @Column(name = "template", nullable = false, length = 100)
    private String template;
}
