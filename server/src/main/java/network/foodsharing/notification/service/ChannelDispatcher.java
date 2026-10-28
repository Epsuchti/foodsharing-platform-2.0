package network.foodsharing.notification.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fans a stored bell out to the {@link NotificationChannel}s (A23): per channel the request must use it, the user's
 * category settings must enable it and the channel must reach the user. PICKUP_REMINDER additionally needs
 * {@code pickup_reminders_enabled} (PU9 master switch). A failing channel is logged and skipped.
 */
@Component
public class ChannelDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ChannelDispatcher.class);

    private final List<NotificationChannel> channels;
    private final NotificationSettingsService settings;

    public ChannelDispatcher(List<NotificationChannel> channels, NotificationSettingsService settings) {
        this.channels = channels;
        this.settings = settings;
    }

    /** @return the channels that delivered */
    public Set<NotificationChannelType> dispatch(User recipient, Bell bell, NotificationRequest request) {
        Set<NotificationChannelType> delivered = EnumSet.noneOf(NotificationChannelType.class);
        if (request.type() == NotificationType.PICKUP_REMINDER && !recipient.isPickupRemindersEnabled()) {
            return delivered;
        }
        for (NotificationChannel channel : channels) {
            if (!channel.applies(request) || !settings.isEnabled(recipient.getId(), request.type(), channel.type())
                    || !channel.available(recipient)) {
                continue;
            }
            try {
                channel.deliver(recipient, bell, request);
                delivered.add(channel.type());
            } catch (RuntimeException ex) {
                log.warn("{} notification {} to {} failed: {}", channel.type(), request.type(), recipient.getId(),
                        ex.toString());
            }
        }
        return delivered;
    }

    /** Delivers through one channel only (settings and availability apply); @return whether it delivered. */
    public boolean dispatchOnly(User recipient, Bell bell, NotificationRequest request, NotificationChannelType type) {
        for (NotificationChannel channel : channels) {
            if (channel.type() != type || !channel.applies(request)
                    || !settings.isEnabled(recipient.getId(), request.type(), type) || !channel.available(recipient)) {
                continue;
            }
            try {
                channel.deliver(recipient, bell, request);
                return true;
            } catch (RuntimeException ex) {
                log.warn("{} notification {} to {} failed: {}", type, request.type(), recipient.getId(), ex.toString());
            }
        }
        return false;
    }
}
