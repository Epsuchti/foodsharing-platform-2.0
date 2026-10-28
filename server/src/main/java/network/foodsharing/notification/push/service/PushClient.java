package network.foodsharing.notification.push.service;

import network.foodsharing.notification.push.domain.PushSubscription;

/** Sends one web push message (A23): {@link WebPushClient} in {@code webpush} mode, {@link LogPushClient} otherwise. */
public interface PushClient {

    /** OK, GONE (404/410: the subscription is dead and gets removed) or FAILED (counted). */
    enum Result { OK, GONE, FAILED }

    /** The message: payload JSON plus the parts the log client captures. */
    record PushMessage(String type, String title, String body, String link, String icon, String tag,
                       java.util.Map<String, String> params, String json) {
    }

    Result send(PushSubscription subscription, PushMessage message);

    /** VAPID public key for the browser (base64url), empty when not configured. */
    String publicKey();
}
