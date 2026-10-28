package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Sign-out alert setting of a store (AU5). */
@Getter
@Setter
@Entity
@Table(name = "signout_alert_setting")
public class SignoutAlertSetting extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "cutoff_enabled", nullable = false)
    private boolean cutoffEnabled = true;

    @Column(name = "maximum_hours_before_pickup", nullable = false)
    private int maximumHoursBeforePickup = 72;

    @Column(name = "channels", nullable = false, length = 100)
    private String channels = "";

    @Column(name = "telegram_chat_ids", length = 1000)
    private String telegramChatIds;

    @Column(name = "active_since")
    private Instant activeSince;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;
}
