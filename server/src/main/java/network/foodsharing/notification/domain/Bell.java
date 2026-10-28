package network.foodsharing.notification.domain;

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
 * A bell notification of one user ({@code bell}). {@code identifier} follows upstream {@code BellType::createIdentifier}
 * ({@code <TYPE>-<id>[-<id>]}) so a later action can delete it (e.g. the new-member bell after verification).
 */
@Getter
@Setter
@Entity
@Table(name = "bell")
public class Bell extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    private NotificationType type;

    @Column(name = "title_key", nullable = false, length = 200)
    private String titleKey;

    /** JSON object of string parameters for the title/body translation. */
    @Column(name = "params", length = 4000)
    private String params;

    @Column(name = "link", length = 500)
    private String link;

    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "identifier", length = 200)
    private String identifier;

    /** Set when the notification was also mailed. */
    @Column(name = "mail_outbox_id")
    private UUID mailOutboxId;

    @Column(name = "read_at")
    private Instant readAt;

    /** Set when at least one web push went out for this bell (M4). */
    @Column(name = "pushed_at")
    private Instant pushedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
