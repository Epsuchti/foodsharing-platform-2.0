package network.foodsharing.notification.push.web;

import network.foodsharing.api.PushApi;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.PushMode;
import network.foodsharing.api.model.PushSubscriptionCreate;
import network.foodsharing.api.model.PushSubscriptionRemove;
import network.foodsharing.api.model.VapidPublicKey;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.push.service.PushClient;
import network.foodsharing.notification.push.service.PushDeliveryService;
import network.foodsharing.notification.push.service.PushSubscriptionService;
import network.foodsharing.security.CurrentActorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Web push subscriptions of the current user (N4, A23). */
@RestController
public class PushController implements PushApi {

    private final PushSubscriptionService subscriptions;
    private final PushDeliveryService delivery;
    private final PushClient client;
    private final CurrentActorService currentActor;
    private final AppProperties properties;

    public PushController(PushSubscriptionService subscriptions, PushDeliveryService delivery, PushClient client,
                          CurrentActorService currentActor, AppProperties properties) {
        this.subscriptions = subscriptions;
        this.delivery = delivery;
        this.client = client;
        this.currentActor = currentActor;
        this.properties = properties;
    }

    @Override
    public ResponseEntity<VapidPublicKey> getVapidPublicKey() {
        currentActor.requireUserId();
        return ResponseEntity.ok(new VapidPublicKey().publicKey(requireKey())
                .mode("log".equals(properties.getPush().getMode()) ? PushMode.LOG : PushMode.WEBPUSH));
    }

    @Override
    public ResponseEntity<Void> subscribePush(PushSubscriptionCreate create) {
        requireKey();
        subscriptions.subscribe(currentActor.requireUserId(), new PushSubscriptionService.NewSubscription(
                create.getEndpoint(), create.getKeys().getP256dh(), create.getKeys().getAuth(), create.getUserAgent()));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unsubscribePush(PushSubscriptionRemove remove) {
        subscriptions.unsubscribe(currentActor.requireUserId(), remove.getEndpoint());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> sendTestPush() {
        requireKey();
        delivery.sendTest(currentActor.requireUser());
        return ResponseEntity.noContent().build();
    }

    private String requireKey() {
        String key = client.publicKey();
        if (key == null || key.isBlank()) {
            throw ApiException.conflict(ApiErrorCode.PUSH_NOT_CONFIGURED);
        }
        return key;
    }
}
