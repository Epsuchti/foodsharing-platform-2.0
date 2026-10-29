package network.foodsharing.notification.telegram.service;

import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationChannel;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.RegionSenderNames;
import network.foodsharing.user.domain.User;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<RegionSenderNames> senderNames;

    public TelegramChannel(TelegramDeliveryService delivery, ObjectProvider<RegionSenderNames> senderNames) {
        this.delivery = delivery;
        this.senderNames = senderNames;
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
        java.util.Map<String, String> params = new java.util.HashMap<>(request.params());
        RegionSenderNames.resolve(senderNames, request.senderRegionId())
                .ifPresent(name -> params.put(RegionSenderNames.MAIL_VARIABLE, name));
        delivery.send(recipient, request.type(), bell.getTitleKey(), params, request.link(),
                request.identifier());
    }
}
