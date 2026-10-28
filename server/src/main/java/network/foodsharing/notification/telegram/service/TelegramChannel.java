package network.foodsharing.notification.telegram.service;

import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationChannel;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.user.domain.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Per-user Telegram alerts (A23) for users with a linked private chat. TELEGRAM_LINKED is skipped: the link flow
 * answers in the chat itself.
 */
@Component
@Order(3)
public class TelegramChannel implements NotificationChannel {

    private final TelegramDeliveryService delivery;

    public TelegramChannel(TelegramDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.TELEGRAM;
    }

    @Override
    public boolean applies(NotificationRequest request) {
        return request.type() != NotificationType.TELEGRAM_LINKED;
    }

    @Override
    public boolean available(User recipient) {
        return recipient.getTelegramChatId() != null && delivery.configured();
    }

    @Override
    public void deliver(User recipient, Bell bell, NotificationRequest request) {
        delivery.send(recipient, request.type(), bell.getTitleKey(), request.params(), request.link(),
                request.identifier());
    }
}
