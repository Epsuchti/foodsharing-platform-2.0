package network.foodsharing.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Channel switches of one user for one {@link NotificationCategory} (A23); no row = defaults. */
@Getter
@Setter
@Entity
@Table(name = "user_notification_setting")
public class UserNotificationSetting extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private NotificationCategory category;

    @Column(name = "mail", nullable = false)
    private boolean mail;

    @Column(name = "push", nullable = false)
    private boolean push;

    @Column(name = "telegram", nullable = false)
    private boolean telegram;
}
