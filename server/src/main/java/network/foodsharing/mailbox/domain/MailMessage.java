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

/**
 * A stored mail (A35) in one folder of a mailbox: incoming mails in INBOX, sent copies in SENT. Addresses are JSON
 * arrays of {@code {address, name}}; {@code bodyHtml} is always sanitised before it is stored.
 */
@Getter
@Setter
@Entity
@Table(name = "mail_message")
public class MailMessage extends BaseEntity {

    public static final int MAX_BODY = 32600;

    @Column(name = "mailbox_id", nullable = false)
    private UUID mailboxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "folder", nullable = false, length = 20)
    private MailFolder folder;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_folder", length = 20)
    private MailFolder previousFolder;

    @Column(name = "message_id", length = 255)
    private String messageId;

    @Column(name = "in_reply_to", length = 255)
    private String inReplyTo;

    @Column(name = "from_address", nullable = false, length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "to_json", nullable = false, length = MAX_BODY)
    private String toJson;

    @Column(name = "cc_json", length = 8000)
    private String ccJson;

    @Column(name = "reply_to", length = 320)
    private String replyTo;

    @Column(name = "subject", nullable = false, length = 998)
    private String subject;

    @Column(name = "body_text", length = MAX_BODY)
    private String bodyText;

    @Column(name = "body_html", length = MAX_BODY)
    private String bodyHtml;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "bounce", nullable = false)
    private boolean bounce;

    @Column(name = "bounced_message_id", length = 255)
    private String bouncedMessageId;

    @Column(name = "raw_size")
    private Long rawSize;

    @Column(name = "spam_score")
    private Double spamScore;

    /** The user who composed a SENT mail (rate limit, A35). */
    @Column(name = "sent_by_user_id")
    private UUID sentByUserId;

    @Column(name = "attachment_count", nullable = false)
    private int attachmentCount;

    /** SHA-256 of the raw MIME of an incoming mail (dedupe per mailbox). */
    @Column(name = "raw_hash", length = 64)
    private String rawHash;
}
