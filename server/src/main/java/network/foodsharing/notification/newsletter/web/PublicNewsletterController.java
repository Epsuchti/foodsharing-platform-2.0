package network.foodsharing.notification.newsletter.web;

import network.foodsharing.api.PublicNewsletterApi;
import network.foodsharing.api.model.NewsletterUnsubscribeInfo;
import network.foodsharing.notification.newsletter.service.NewsletterUnsubscribeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** One-click newsletter unsubscribe (M4 B5, N7); works logged out. */
@RestController
public class PublicNewsletterController implements PublicNewsletterApi {

    private final NewsletterUnsubscribeService unsubscribe;

    public PublicNewsletterController(NewsletterUnsubscribeService unsubscribe) {
        this.unsubscribe = unsubscribe;
    }

    @Override
    public ResponseEntity<Void> confirmNewsletterUnsubscribe(String token) {
        unsubscribe.unsubscribe(token);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<NewsletterUnsubscribeInfo> getNewsletterUnsubscribe(String token) {
        NewsletterUnsubscribeInfo info = new NewsletterUnsubscribeInfo();
        info.setEmail(unsubscribe.maskedEmail(token));
        return ResponseEntity.ok(info);
    }
}
