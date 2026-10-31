package network.foodsharing.notification.push.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationAction;
import network.foodsharing.notification.push.domain.PushSubscription;
import network.foodsharing.notification.push.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Web push subscriptions of the current user (A23): upsert by endpoint, at most {@code max-subscriptions-per-user}. */
@Service
public class PushSubscriptionService {

    /** A subscription as the browser sends it. */
    public record NewSubscription(String endpoint, String p256dh, String auth, String userAgent) {
    }

    private final PushSubscriptionRepository repository;
    private final HistoryService history;
    private final AppProperties properties;
    private final Clock clock;

    public PushSubscriptionService(PushSubscriptionRepository repository, HistoryService history,
                                   AppProperties properties, Clock clock) {
        this.repository = repository;
        this.history = history;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Stores (or moves to this user / refreshes) the subscription of one browser.
     *
     * @throws ApiException 409 TOO_MANY_PUSH_SUBSCRIPTIONS
     */
    @Transactional
    public PushSubscription subscribe(UUID userId, NewSubscription subscription) {
        String hash = hash(subscription.endpoint());
        PushSubscription row = repository.findByEndpointHash(hash).orElse(null);
        if (row == null || !row.getUserId().equals(userId)) {
            if (repository.countByUserId(userId) >= properties.getPush().getMaxSubscriptionsPerUser()) {
                throw ApiException.conflict(ApiErrorCode.TOO_MANY_PUSH_SUBSCRIPTIONS);
            }
        }
        if (row == null) {
            row = new PushSubscription();
            row.setEndpoint(subscription.endpoint());
            row.setEndpointHash(hash);
        }
        row.setUserId(userId);
        row.setP256dh(subscription.p256dh());
        row.setAuth(subscription.auth());
        row.setUserAgent(subscription.userAgent());
        row.setFailureCount(0);
        PushSubscription saved = repository.save(row);
        history.record(history.event(NotificationAction.PUSH_SUBSCRIBED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(userId).metadata(Map.of("subscriptionId", saved.getId().toString())).build());
        return saved;
    }

    /** Removes one of my subscriptions; unknown or foreign endpoints are ignored. */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.findByEndpointHash(hash(endpoint)).filter(row -> row.getUserId().equals(userId)).ifPresent(row -> {
            repository.delete(row);
            history.record(history.event(NotificationAction.PUSH_UNSUBSCRIBED, HistoryService.SYSTEM_SUBJECT_ID)
                    .secondarySubject(userId).metadata(Map.of("subscriptionId", row.getId().toString())).build());
        });
    }

    /**
     * Removes one of my subscriptions by id.
     *
     * @throws ApiException 404 RESOURCE_NOT_FOUND (unknown or foreign)
     */
    @Transactional
    public void remove(UUID userId, UUID subscriptionId) {
        PushSubscription row = repository.findById(subscriptionId).filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.RESOURCE_NOT_FOUND));
        repository.delete(row);
        history.record(history.event(NotificationAction.PUSH_UNSUBSCRIBED, HistoryService.SYSTEM_SUBJECT_ID)
                .secondarySubject(userId).metadata(Map.of("subscriptionId", row.getId().toString())).build());
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> subscriptions(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    @Transactional(readOnly = true)
    public boolean hasSubscription(UUID userId) {
        return repository.existsByUserId(userId);
    }

    /** Applies a delivery result: OK resets the failure count, GONE deletes, FAILED counts. */
    @Transactional
    @NoHistory(reason = "delivery bookkeeping of a subscription; the notification is recorded as NOTIFICATION_SENT")
    public void recordResult(UUID subscriptionId, PushClient.Result result) {
        repository.findById(subscriptionId).ifPresent(row -> {
            switch (result) {
                case OK -> {
                    row.setLastSuccessAt(clock.instant());
                    row.setFailureCount(0);
                }
                case GONE -> repository.delete(row);
                case FAILED -> row.setFailureCount(row.getFailureCount() + 1);
            }
        });
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
