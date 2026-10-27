package network.foodsharing.intropickup.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Language;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.intropickup.domain.IntroPickupAction;
import network.foodsharing.intropickup.domain.IntroPickupSubscription;
import network.foodsharing.intropickup.domain.SubscriptionChannel;
import network.foodsharing.intropickup.repository.IntroPickupSubscriptionRepository;
import network.foodsharing.region.service.RegionPermissionResolver;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscriptions to "new bookable EinAb" notifications (plan §14.1 EA9): one per user, only for unverified members of
 * the region; the unsubscribe token is rotated whenever the subscription becomes active again.
 */
@Service
public class IntroPickupSubscriptionService {

    /** After commit: confirmation bell (and mail for the e-mail channel). */
    public record Subscribed(UUID subscriptionId) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final IntroPickupSubscriptionRepository subscriptions;
    private final RegionService regions;
    private final RegionPermissionResolver resolver;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ApplicationEventPublisher events;

    public IntroPickupSubscriptionService(IntroPickupSubscriptionRepository subscriptions, RegionService regions,
                                          RegionPermissionResolver resolver, CurrentActorService currentActor,
                                          HistoryService history, ApplicationEventPublisher events) {
        this.subscriptions = subscriptions;
        this.regions = regions;
        this.resolver = resolver;
        this.currentActor = currentActor;
        this.history = history;
        this.events = events;
    }

    /** @throws ApiException 404 RESOURCE_NOT_FOUND */
    @Transactional(readOnly = true)
    public IntroPickupSubscription mine() {
        return subscriptions.findByUserId(currentActor.requireUserId())
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * @throws ApiException 404 REGION_NOT_FOUND, 409 NOTIFICATION_NOT_NEEDED_VERIFIED / NOT_REGION_MEMBER,
     *                      400 VALIDATION_FAILED (e-mail channel without an address)
     */
    @Transactional
    public IntroPickupSubscription subscribe(UUID regionId, SubscriptionChannel channel, String deliveryEmail,
                                             Language language) {
        regions.require(regionId);
        User user = currentActor.requireUser();
        if (user.isVerified()) {
            throw ApiException.conflict(ApiErrorCode.NOTIFICATION_NOT_NEEDED_VERIFIED);
        }
        if (!resolver.isMember(user.getId(), regionId)) {
            throw ApiException.conflict(ApiErrorCode.NOT_REGION_MEMBER);
        }
        String email = deliveryEmail == null || deliveryEmail.isBlank() ? null : deliveryEmail.trim();
        if (channel == SubscriptionChannel.EMAIL && email == null) {
            email = user.getEmail();
        }
        if (email != null && (!email.contains("@") || email.length() > 320)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        IntroPickupSubscription subscription = subscriptions.findByUserId(user.getId()).orElseGet(() -> {
            IntroPickupSubscription created = new IntroPickupSubscription();
            created.setUserId(user.getId());
            created.setActive(false);
            return created;
        });
        if (!subscription.isActive() || subscription.getUnsubscribeToken() == null) {
            subscription.setUnsubscribeToken(newToken());
        }
        subscription.setRegionId(regionId);
        subscription.setChannel(channel);
        subscription.setDeliveryEmail(channel == SubscriptionChannel.EMAIL ? email : null);
        subscription.setLanguage((language == null ? user.getPreferredLanguage() : language).getValue());
        subscription.setActive(true);
        subscriptions.save(subscription);
        history.record(history.event(IntroPickupAction.SUBSCRIBED, user.getId()).region(regionId)
                .metadata("channel", channel.name()).build());
        events.publishEvent(new Subscribed(subscription.getId()));
        return subscription;
    }

    /** @throws ApiException 404 RESOURCE_NOT_FOUND */
    @Transactional
    public void unsubscribeMine() {
        IntroPickupSubscription subscription = mine();
        deactivate(subscription, "self");
    }

    /** @throws ApiException 400 INVALID_UNSUBSCRIBE_TOKEN */
    @Transactional
    public void unsubscribeByToken(String token) {
        IntroPickupSubscription subscription = token == null ? null
                : subscriptions.findByUnsubscribeToken(token.trim()).orElse(null);
        if (subscription == null) {
            throw ApiException.badRequest(ApiErrorCode.INVALID_UNSUBSCRIBE_TOKEN);
        }
        deactivate(subscription, "token");
    }

    /** Verified users need no more EinAbs. */
    @Transactional
    public void deactivateForVerifiedUser(UUID userId) {
        Optional<IntroPickupSubscription> subscription = subscriptions.findByUserId(userId)
                .filter(IntroPickupSubscription::isActive);
        subscription.ifPresent(s -> deactivate(s, "verified"));
    }

    private void deactivate(IntroPickupSubscription subscription, String via) {
        if (!subscription.isActive()) {
            return;
        }
        subscription.setActive(false);
        history.record(history.event(IntroPickupAction.UNSUBSCRIBED, subscription.getUserId())
                .region(subscription.getRegionId()).metadata("via", via).build());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
