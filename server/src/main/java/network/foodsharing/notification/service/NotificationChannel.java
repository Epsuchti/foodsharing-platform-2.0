package network.foodsharing.notification.service;

import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.user.domain.User;

/**
 * A channel besides the bell (A23): mail (outbox), web push, Telegram. {@link ChannelDispatcher} calls
 * {@link #deliver} for every recipient whose settings enable the channel for the type's category and for whom the
 * channel is {@link #available}. Failures are logged by the dispatcher and never fail the caller.
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /** Whether the channel can reach this user at all (push subscription, linked Telegram chat, …). */
    boolean available(User recipient);

    /** Whether this request uses the channel (e.g. mail only when the request names a template). */
    default boolean applies(NotificationRequest request) {
        return true;
    }

    /** Delivers one notification; the bell is managed, so channels may set its mail/push markers. */
    void deliver(User recipient, Bell bell, NotificationRequest request);
}
