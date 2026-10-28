package network.foodsharing.automation.domain;

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

/** One advert outcome per (advert, pickup, trigger hours), or a dry-run summary. */
@Getter
@Setter
@Entity
@Table(name = "open_slot_advert_audit")
public class OpenSlotAdvertAudit extends BaseEntity {

    @Column(name = "advert_id")
    private UUID advertId;

    @Column(name = "advert_number")
    private Integer advertNumber;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Column(name = "pickup_at")
    private Instant pickupAt;

    @Column(name = "trigger_hours_before")
    private Integer triggerHoursBefore;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "telegram_chat_id", length = 40)
    private String telegramChatId;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "telegram_deleted_at")
    private Instant telegramDeletedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 60)
    private AdvertStatus status;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "message", length = 4000)
    private String message;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "error", length = 2000)
    private String error;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "filled_at")
    private Instant filledAt;
}
