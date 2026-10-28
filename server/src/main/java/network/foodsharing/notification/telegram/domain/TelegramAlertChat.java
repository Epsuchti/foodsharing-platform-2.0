package network.foodsharing.notification.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A region or store alert chat (table {@code telegram_chat}, A28). */
@Getter
@Setter
@Entity
@Table(name = "telegram_chat")
public class TelegramAlertChat extends BaseEntity {

    /** Owner kind of an alert chat. */
    public enum OwnerType { REGION, STORE }

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "chat_id", nullable = false, length = 40)
    private String chatId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
