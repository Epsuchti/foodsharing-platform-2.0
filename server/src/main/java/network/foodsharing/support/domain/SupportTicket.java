package network.foodsharing.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A support request from the support form (C9); {@code userId} null for the public form. */
@Getter
@Setter
@Entity
@Table(name = "support_ticket")
public class SupportTicket extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, length = 32600)
    private String body;

    @Column(name = "page", length = 500)
    private String page;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Comma-separated upload ids (claimed as SUPPORT_ATTACHMENT). */
    @Column(name = "attachment_upload_ids", length = 400)
    private String attachmentUploadIds;
}
