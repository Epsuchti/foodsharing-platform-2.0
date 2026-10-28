package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Open-slot advert of a store (AU4): numbered 1..5, messages as JSON arrays, channels as CSV. */
@Getter
@Setter
@Entity
@Table(name = "open_slot_advert")
public class OpenSlotAdvert extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "advert_number", nullable = false)
    private int advertNumber;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Column(name = "trigger_hours_before", nullable = false)
    private int triggerHoursBefore = 24;

    @Column(name = "channels", nullable = false, length = 100)
    private String channels = "";

    @Column(name = "telegram_chat_id", length = 40)
    private String telegramChatId;

    @Column(name = "store_messages", length = 32600)
    private String storeMessages;

    @Column(name = "push_messages", length = 32600)
    private String pushMessages;

    @Column(name = "telegram_messages", length = 32600)
    private String telegramMessages;

    @Column(name = "send_late_cancellation_message", nullable = false)
    private boolean sendLateCancellationMessage;

    @Column(name = "late_cancellation_message", nullable = false, length = 2000)
    private String lateCancellationMessage;

    @Column(name = "late_cancellation_max_hours", nullable = false)
    private int lateCancellationMaxHours = 72;

    @Column(name = "resend_latest_advert_after_late_cancellation", nullable = false)
    private boolean resendLatestAdvertAfterLateCancellation = true;
}
