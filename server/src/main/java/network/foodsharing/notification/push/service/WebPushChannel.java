package network.foodsharing.notification.push.service;

import java.time.Clock;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.service.NotificationChannel;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.user.domain.User;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Web push for every bell of users with at least one subscription (A23); sets {@code bell.pushed_at}. */
@Component
@Order(2)
public class WebPushChannel implements NotificationChannel {

    private final PushDeliveryService delivery;
    private final PushSubscriptionService subscriptions;
    private final Clock clock;

    public WebPushChannel(PushDeliveryService delivery, PushSubscriptionService subscriptions, Clock clock) {
        this.delivery = delivery;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public boolean available(User recipient) {
        return subscriptions.hasSubscription(recipient.getId());
    }

    @Override
    public void deliver(User recipient, Bell bell, NotificationRequest request) {
        if (delivery.send(recipient, request.type(), bell.getTitleKey(), request.params(), request.link(),
                request.icon(), request.identifier())) {
            bell.setPushedAt(clock.instant());
        }
    }
}
