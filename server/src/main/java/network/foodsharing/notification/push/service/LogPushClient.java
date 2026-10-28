package network.foodsharing.notification.push.service;

import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.domain.NotificationChannelType;
import network.foodsharing.notification.push.domain.PushSubscription;
import network.foodsharing.notification.service.CapturedMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code app.push.mode=log} (local/e2e/test): pushes are logged and captured for the dev API, never sent. */
@Component
@ConditionalOnProperty(name = "app.push.mode", havingValue = "log")
public class LogPushClient implements PushClient {

    private static final Logger log = LoggerFactory.getLogger(LogPushClient.class);

    private final CapturedMessages captured;
    private final AppProperties properties;

    public LogPushClient(CapturedMessages captured, AppProperties properties) {
        this.captured = captured;
        this.properties = properties;
    }

    @Override
    public Result send(PushSubscription subscription, PushMessage message) {
        captured.add(subscription.getUserId(), message.type(), NotificationChannelType.PUSH, message.params(),
                message.link(), message.tag(), message.title(), message.body(), subscription.getEndpoint());
        log.info("Push (log mode) to {}: {}", subscription.getUserId(), message.title());
        return Result.OK;
    }

    @Override
    public String publicKey() {
        return properties.getPush().getVapid().getPublicKey();
    }
}
