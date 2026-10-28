package network.foodsharing.notification.newsletter.web;

import java.util.UUID;
import network.foodsharing.api.NewslettersApi;
import network.foodsharing.api.model.Newsletter;
import network.foodsharing.api.model.NewsletterCreate;
import network.foodsharing.api.model.NewsletterPage;
import network.foodsharing.api.model.NewsletterPatch;
import network.foodsharing.api.model.NewsletterProgress;
import network.foodsharing.api.model.NewsletterStatus;
import network.foodsharing.api.model.RecipientCount;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Newsletters (M4 B5).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class NewslettersController implements NewslettersApi {

    @Override
    public ResponseEntity<Newsletter> cancelNewsletter(UUID newsletterId) {
        throw ApiException.notImplemented("cancelNewsletter");
    }

    @Override
    public ResponseEntity<RecipientCount> countNewsletterRecipients(UUID newsletterId) {
        throw ApiException.notImplemented("countNewsletterRecipients");
    }

    @Override
    public ResponseEntity<Newsletter> createNewsletter(NewsletterCreate newsletterCreate) {
        throw ApiException.notImplemented("createNewsletter");
    }

    @Override
    public ResponseEntity<Void> deleteNewsletter(UUID newsletterId) {
        throw ApiException.notImplemented("deleteNewsletter");
    }

    @Override
    public ResponseEntity<Newsletter> getNewsletter(UUID newsletterId) {
        throw ApiException.notImplemented("getNewsletter");
    }

    @Override
    public ResponseEntity<NewsletterProgress> getNewsletterProgress(UUID newsletterId) {
        throw ApiException.notImplemented("getNewsletterProgress");
    }

    @Override
    public ResponseEntity<NewsletterPage> listNewsletters(UUID regionId, NewsletterStatus status, Integer page, Integer size) {
        throw ApiException.notImplemented("listNewsletters");
    }

    @Override
    public ResponseEntity<Newsletter> queueNewsletter(UUID newsletterId) {
        throw ApiException.notImplemented("queueNewsletter");
    }

    @Override
    public ResponseEntity<Void> sendNewsletterTest(UUID newsletterId) {
        throw ApiException.notImplemented("sendNewsletterTest");
    }

    @Override
    public ResponseEntity<Newsletter> updateNewsletter(UUID newsletterId, NewsletterPatch newsletterPatch) {
        throw ApiException.notImplemented("updateNewsletter");
    }
}
