package network.foodsharing.notification.newsletter.web;

import network.foodsharing.api.PublicNewsletterApi;
import network.foodsharing.api.model.NewsletterUnsubscribeInfo;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-click newsletter unsubscribe (M4 B5).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class PublicNewsletterController implements PublicNewsletterApi {

    @Override
    public ResponseEntity<Void> confirmNewsletterUnsubscribe(String token) {
        throw ApiException.notImplemented("confirmNewsletterUnsubscribe");
    }

    @Override
    public ResponseEntity<NewsletterUnsubscribeInfo> getNewsletterUnsubscribe(String token) {
        throw ApiException.notImplemented("getNewsletterUnsubscribe");
    }
}
