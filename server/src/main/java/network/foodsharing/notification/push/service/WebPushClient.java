package network.foodsharing.notification.push.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import network.foodsharing.common.integration.Integration;
import network.foodsharing.common.integration.IntegrationRegistry;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.push.domain.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

/**
 * {@code app.push.mode=webpush} (prod): VAPID-signed web push through {@code nl.martijndwars:web-push}, guarded by the
 * WEB_PUSH circuit breaker (A26). 404/410 → GONE, other 4xx → FAILED, 5xx/timeouts are reported as outage.
 */
@Component
@ConditionalOnProperty(name = "app.push.mode", havingValue = "webpush", matchIfMissing = true)
public class WebPushClient implements PushClient {

    private final AppProperties.Push settings;
    private final IntegrationRegistry integrations;
    private volatile PushService service;

    public WebPushClient(AppProperties properties, IntegrationRegistry integrations) {
        this.settings = properties.getPush();
        this.integrations = integrations;
        integrations.registerProbe(Integration.WEB_PUSH, () -> !publicKey().isBlank());
    }

    @Override
    public String publicKey() {
        return settings.getVapid().getPublicKey() == null ? "" : settings.getVapid().getPublicKey();
    }

    @Override
    public Result send(PushSubscription subscription, PushMessage message) {
        int status = integrations.call(Integration.WEB_PUSH, "push-send", () -> post(subscription, message));
        if (status == 404 || status == 410) {
            return Result.GONE;
        }
        return status >= 200 && status < 300 ? Result.OK : Result.FAILED;
    }

    private int post(PushSubscription subscription, PushMessage message) {
        try {
            HttpResponse response = service().send(new Notification(subscription.getEndpoint(),
                    subscription.getP256dh(), subscription.getAuth(), message.json().getBytes(StandardCharsets.UTF_8)));
            int status = response.getStatusLine().getStatusCode();
            if (status >= 500) {
                throw HttpServerErrorException.create(HttpStatus.valueOf(status), "push service", null, null, null);
            }
            return status;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) { // GeneralSecurityException, JoseException, ExecutionException
            throw new IllegalStateException("web push failed: " + ex.getMessage(), ex);
        }
    }

    private PushService service() throws GeneralSecurityException {
        if (service == null) {
            synchronized (this) {
                if (service == null) {
                    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    service = new PushService(settings.getVapid().getPublicKey(), settings.getVapid().getPrivateKey(),
                            settings.getVapid().getSubject());
                }
            }
        }
        return service;
    }
}
