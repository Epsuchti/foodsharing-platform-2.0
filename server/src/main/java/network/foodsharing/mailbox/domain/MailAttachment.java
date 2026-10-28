package network.foodsharing.mailbox.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** An attachment of a stored mail; the bytes are an upload claimed by module {@code mailbox} (A35). */
@Getter
@Setter
@Entity
@Table(name = "mail_attachment")
public class MailAttachment extends BaseEntity {

    @Column(name = "mail_id", nullable = false)
    private UUID mailId;

    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size", nullable = false)
    private long size;

    /** Content-ID of an inline part ({@code cid:} references in the HTML). */
    @Column(name = "content_id", length = 255)
    private String contentId;
}
