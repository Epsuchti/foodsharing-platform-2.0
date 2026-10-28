package network.foodsharing.notification.push.web;

import network.foodsharing.api.PushApi;
import network.foodsharing.api.model.PushSubscriptionCreate;
import network.foodsharing.api.model.PushSubscriptionRemove;
import network.foodsharing.api.model.VapidPublicKey;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web push subscriptions (M4 B2).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class PushController implements PushApi {

    @Override
    public ResponseEntity<VapidPublicKey> getVapidPublicKey() {
        throw ApiException.notImplemented("getVapidPublicKey");
    }

    @Override
    public ResponseEntity<Void> sendTestPush() {
        throw ApiException.notImplemented("sendTestPush");
    }

    @Override
    public ResponseEntity<Void> subscribePush(PushSubscriptionCreate pushSubscriptionCreate) {
        throw ApiException.notImplemented("subscribePush");
    }

    @Override
    public ResponseEntity<Void> unsubscribePush(PushSubscriptionRemove pushSubscriptionRemove) {
        throw ApiException.notImplemented("unsubscribePush");
    }
}
