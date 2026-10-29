package network.foodsharing.basket.service;

import network.foodsharing.admin.domain.FeatureToggle;
import network.foodsharing.admin.service.FeatureToggleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.basket.domain.Basket;
import network.foodsharing.basket.domain.BasketAction;
import network.foodsharing.basket.domain.BasketGrocery;
import network.foodsharing.basket.domain.BasketKind;
import network.foodsharing.basket.domain.BasketKindEntry;
import network.foodsharing.basket.domain.BasketLocationType;
import network.foodsharing.basket.domain.BasketRequestEntry;
import network.foodsharing.basket.domain.BasketRequestStatus;
import network.foodsharing.basket.domain.BasketStatus;
import network.foodsharing.basket.repository.BasketGroceryRepository;
import network.foodsharing.basket.repository.BasketKindEntryRepository;
import network.foodsharing.basket.repository.BasketRepository;
import network.foodsharing.basket.repository.BasketRequestEntryRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.web.SlidingWindowRateLimiter;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.HistoryEvent;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StoreCategoryService;
import network.foodsharing.store.service.TeamChatPort;
import network.foodsharing.upload.domain.Upload;
import network.foodsharing.upload.service.UploadService;
import network.foodsharing.upload.service.UploadUsage;
import network.foodsharing.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Food baskets (B1, B2; upstream {@code BasketPermissions}/{@code BasketTransactions}): any signed-in user offers
 * baskets, others request them; the owner answers requests (PICKED_UP takes the basket).
 */
@Service
public class BasketService {

    public static final String PHOTO_MODULE = "BASKET_PHOTO";

    /** Changes of a basket; null = unchanged (update) or default (create). */
    public record BasketInput(String description, BasketLocationType locationType, Boolean contactMessage,
                              Boolean contactPhone, Boolean contactMobile, Integer lifetimeDays, Integer weightGrams,
                              List<UUID> groceryIds, List<BasketKind> kinds, Double lat, Double lon, String phone,
                              String mobile, UUID photoUploadId) {
    }

    private final BasketRepository baskets;
    private final BasketGroceryRepository groceries;
    private final BasketKindEntryRepository kinds;
    private final BasketRequestEntryRepository requests;
    private final CurrentActorService actors;
    private final PermissionService permissions;
    private final StoreCategoryService groceryCatalog;
    private final UploadService uploads;
    private final NotificationService notifications;
    private final TeamChatPort chat;
    private final HistoryService history;
    private final Clock clock;

    private final FeatureToggleService toggles;
    private final SlidingWindowRateLimiter requestLimiter;

    public BasketService(BasketRepository baskets, BasketGroceryRepository groceries, BasketKindEntryRepository kinds,
                         BasketRequestEntryRepository requests, CurrentActorService actors,
                         PermissionService permissions, StoreCategoryService groceryCatalog, UploadService uploads,
                         NotificationService notifications, TeamChatPort chat, HistoryService history, Clock clock,
            FeatureToggleService toggles, AppProperties properties) {
        this.toggles = toggles;
        this.requestLimiter = new SlidingWindowRateLimiter("basket-requests", clock,
                Math.max(1, properties.getBaskets().getMaxRequestsPerHour()), Duration.ofHours(1));
        this.baskets = baskets;
        this.groceries = groceries;
        this.kinds = kinds;
        this.requests = requests;
        this.actors = actors;
        this.permissions = permissions;
        this.groceryCatalog = groceryCatalog;
        this.uploads = uploads;
        this.notifications = notifications;
        this.chat = chat;
        this.history = history;
        this.clock = clock;
    }

    public static String link(UUID basketId) {
        return "/baskets/" + basketId;
    }

    static String requestBell(UUID basketId, UUID requesterId) {
        return NotificationType.BASKET_REQUEST.name() + "-" + basketId + "-" + requesterId;
    }

    static String answerBell(UUID basketId, UUID requesterId) {
        return NotificationType.BASKET_REQUEST_ANSWERED.name() + "-" + basketId + "-" + requesterId;
    }

    /** @throws ApiException 400 validation (see {@link BasketRules#validate}) */
    @Transactional
    public Basket create(BasketInput in) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        User owner = actors.requireUser();
        Basket basket = new Basket();
        basket.setOwnerId(owner.getId());
        basket.setStatus(BasketStatus.ACTIVE);
        basket.setDescription(strip(in.description()));
        basket.setContactMessage(Boolean.TRUE.equals(in.contactMessage()));
        basket.setContactPhone(Boolean.TRUE.equals(in.contactPhone()));
        basket.setContactMobile(Boolean.TRUE.equals(in.contactMobile()));
        basket.setPhone(in.phone());
        basket.setMobile(in.mobile());
        basket.setWeightGrams(in.weightGrams());
        basket.setLifetimeDays(in.lifetimeDays() == null ? BasketRules.DEFAULT_LIFETIME_DAYS : in.lifetimeDays());
        locate(basket, owner, in.locationType() == null ? BasketLocationType.HOME : in.locationType(), in.lat(),
                in.lon());
        BasketRules.validate(basket);
        Instant now = clock.instant();
        basket.setExpiresAt(now.plus(Duration.ofDays(basket.getLifetimeDays())));
        Basket saved = baskets.save(basket);
        replaceGroceries(saved.getId(), in.groceryIds());
        replaceKinds(saved.getId(), in.kinds());
        if (in.photoUploadId() != null) {
            claimPhoto(saved, in.photoUploadId(), owner.getId());
        }
        history.record(history.event(BasketAction.CREATED, saved.getId())
                .metadata("locationType", saved.getLocationType().name())
                .metadata("lifetimeDays", saved.getLifetimeDays()).build());
        return saved;
    }

    /** Owner only, while ACTIVE. @throws ApiException 404, 403 ACCESS_DENIED, 409 BASKET_NOT_ACTIVE, 400 */
    @Transactional
    public Basket update(UUID basketId, BasketInput in) {
        return update(basketId, in, false);
    }

    /**
     * {@code removePhoto}: drop the photo and delete its upload as on replacement (ignored when a new photo is given).
     *
     * @throws ApiException 404, 403 ACCESS_DENIED, 409 BASKET_NOT_ACTIVE, 400
     */
    @Transactional
    public Basket update(UUID basketId, BasketInput in, boolean removePhoto) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        User owner = actors.requireUser();
        Basket basket = require(basketId);
        if (!basket.getOwnerId().equals(owner.getId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (!BasketRules.isOpen(basket, clock.instant())) {
            throw ApiException.conflict(ApiErrorCode.BASKET_NOT_ACTIVE);
        }
        HistoryEvent.Builder event = history.event(BasketAction.UPDATED, basketId);
        boolean changed = false;
        changed |= set(event, "description", basket.getDescription(), strip(in.description()),
                basket::setDescription);
        changed |= set(event, "contactMessage", basket.isContactMessage(), in.contactMessage(),
                basket::setContactMessage);
        changed |= set(event, "contactPhone", basket.isContactPhone(), in.contactPhone(), basket::setContactPhone);
        changed |= set(event, "contactMobile", basket.isContactMobile(), in.contactMobile(),
                basket::setContactMobile);
        changed |= set(event, "phone", basket.getPhone(), in.phone(), basket::setPhone);
        changed |= set(event, "mobile", basket.getMobile(), in.mobile(), basket::setMobile);
        changed |= set(event, "weightGrams", basket.getWeightGrams(), in.weightGrams(), basket::setWeightGrams);
        if (in.locationType() != null || in.lat() != null || in.lon() != null) {
            BasketLocationType type = in.locationType() == null ? basket.getLocationType() : in.locationType();
            double oldLat = basket.getLat();
            double oldLon = basket.getLon();
            BasketLocationType oldType = basket.getLocationType();
            locate(basket, owner, type, in.lat() == null ? basket.getLat() : in.lat(),
                    in.lon() == null ? basket.getLon() : in.lon());
            if (oldType != type || oldLat != basket.getLat() || oldLon != basket.getLon()) {
                event.change("location", oldType, type);
                changed = true;
            }
        }
        BasketRules.validate(basket);
        if (in.groceryIds() != null) {
            replaceGroceries(basketId, in.groceryIds());
            event.change("groceries", null, in.groceryIds().size());
            changed = true;
        }
        if (in.kinds() != null) {
            replaceKinds(basketId, in.kinds());
            event.change("kinds", null, in.kinds().toString());
            changed = true;
        }
        if (removePhoto && in.photoUploadId() == null && basket.getPhotoUploadId() != null) {
            UUID previous = basket.getPhotoUploadId();
            basket.setPhotoUploadId(null);
            deletePhoto(previous);
            event.change("photo", previous, null);
            changed = true;
        }
        if (in.photoUploadId() != null && !in.photoUploadId().equals(basket.getPhotoUploadId())) {
            UUID previous = basket.getPhotoUploadId();
            claimPhoto(basket, in.photoUploadId(), owner.getId());
            deletePhoto(previous);
            event.change("photo", previous, in.photoUploadId());
            changed = true;
        }
        if (changed) {
            history.record(event.build());
        }
        return basket;
    }

    /** Owner or ORGA. @throws ApiException 404, 403 ACCESS_DENIED */
    @Transactional
    public void delete(UUID basketId) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        SessionPrincipal actor = actors.requirePrincipal();
        Basket basket = require(basketId);
        if (!BasketRules.mayDelete(basket, actor.userId(), permissions.isOrga(actor))) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        history.record(history.event(BasketAction.DELETED, basketId)
                .change("status", basket.getStatus(), BasketStatus.DELETED).build());
        markDeleted(basket);
    }

    /**
     * Requests a basket; the owner gets a bell and, when the basket allows messages and a text was given, a direct
     * chat message. @throws ApiException 404, 400 BASKET_OWN, 409 BASKET_NOT_ACTIVE / BASKET_ALREADY_REQUESTED,
     * 429 BASKET_REQUEST_RATE_LIMITED
     */
    @Transactional
    public BasketRequestEntry request(UUID basketId, String rawMessage) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        User requester = actors.requireUser();
        Basket basket = require(basketId);
        if (basket.getOwnerId().equals(requester.getId())) {
            throw ApiException.badRequest(ApiErrorCode.BASKET_OWN);
        }
        if (!BasketRules.isOpen(basket, clock.instant())) {
            throw ApiException.conflict(ApiErrorCode.BASKET_NOT_ACTIVE);
        }
        String message = strip(rawMessage);
        if (message != null && message.length() > 2000) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "message");
        }
        if (!requestLimiter.tryAcquire(requester.getId().toString())) {
            throw ApiException.tooManyRequests(ApiErrorCode.BASKET_REQUEST_RATE_LIMITED, requestLimiter.window());
        }
        BasketRequestEntry request = requests.findByBasketIdAndRequesterId(basketId, requester.getId())
                .orElse(null);
        if (request != null && BasketRules.isLive(request.getStatus())) {
            throw ApiException.conflict(ApiErrorCode.BASKET_ALREADY_REQUESTED);
        }
        if (request == null) {
            request = new BasketRequestEntry();
            request.setBasketId(basketId);
            request.setRequesterId(requester.getId());
            basket.setRequestCount(basket.getRequestCount() + 1);
        }
        Instant now = clock.instant();
        request.setStatus(BasketRequestStatus.REQUESTED);
        request.setMessage(basket.isContactMessage() ? message : null);
        request.setRequestedAt(now);
        request.setAnsweredAt(null);
        BasketRequestEntry saved = requests.save(request);
        history.record(history.event(BasketAction.REQUESTED, basketId)
                .metadata("requesterId", requester.getId().toString()).build());
        notifications.notify(NotificationRequest.to(List.of(basket.getOwnerId()), NotificationType.BASKET_REQUEST)
                .param("name", requester.getDisplayName()).link(link(basketId))
                .identifier(requestBell(basketId, requester.getId())).build());
        if (basket.isContactMessage() && message != null) {
            chat.sendDirect(requester.getId(), basket.getOwnerId(), message);
        }
        return saved;
    }

    /** The requester withdraws an open request. @throws ApiException 404 BASKET_NOT_FOUND */
    @Transactional
    public void withdraw(UUID basketId) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID requesterId = actors.requireUserId();
        BasketRequestEntry request = requests.findByBasketIdAndRequesterId(basketId, requesterId)
                .filter(r -> r.getStatus() == BasketRequestStatus.REQUESTED
                        || r.getStatus() == BasketRequestStatus.READ)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BASKET_NOT_FOUND));
        history.record(history.event(BasketAction.REQUEST_WITHDRAWN, basketId)
                .metadata("requesterId", requesterId.toString()).build());
        request.setStatus(BasketRequestStatus.WITHDRAWN);
        notifications.removeBells(requestBell(basketId, requesterId));
    }

    /**
     * The owner answers a request; PICKED_UP marks the basket TAKEN. The requester gets a bell.
     *
     * @throws ApiException 404, 403 ACCESS_DENIED, 400 VALIDATION_FAILED
     */
    @Transactional
    public BasketRequestEntry answer(UUID basketId, UUID requesterId, BasketRequestStatus status) {
        toggles.requireEnabled(FeatureToggle.BASKETS);
        UUID ownerId = actors.requireUserId();
        Basket basket = require(basketId);
        if (!basket.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        if (status != BasketRequestStatus.PICKED_UP && status != BasketRequestStatus.DENIED
                && status != BasketRequestStatus.NOT_PICKED_UP) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "status");
        }
        BasketRequestEntry request = requests.findByBasketIdAndRequesterId(basketId, requesterId)
                .filter(r -> r.getStatus() != BasketRequestStatus.WITHDRAWN)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BASKET_NOT_FOUND));
        Instant now = clock.instant();
        if (request.getStatus() != status) {
            history.record(history.event(BasketAction.REQUEST_STATUS_CHANGED, basketId)
                    .change("status", request.getStatus(), status)
                    .metadata("requesterId", requesterId.toString()).build());
            request.setStatus(status);
            request.setAnsweredAt(now);
            notifications.removeBells(requestBell(basketId, requesterId));
            notifications.notify(NotificationRequest.to(List.of(requesterId),
                            NotificationType.BASKET_REQUEST_ANSWERED).param("status", status.name())
                    .link(link(basketId)).identifier(answerBell(basketId, requesterId)).build());
        }
        if (status == BasketRequestStatus.PICKED_UP && basket.getStatus() == BasketStatus.ACTIVE) {
            history.record(history.event(BasketAction.FIELD_CHANGED, basketId)
                    .change("status", basket.getStatus(), BasketStatus.TAKEN).build());
            basket.setStatus(BasketStatus.TAKEN);
            basket.setTakenAt(now);
        }
        return request;
    }

    /**
     * The owner opened the basket: open requests REQUESTED → READ (upstream "request message read"), each recorded as
     * REQUEST_STATUS_CHANGED. Anyone else: nothing happens.
     *
     * @return the number of requests marked read
     */
    @Transactional
    public int markRequestsRead(UUID basketId) {
        UUID viewerId = actors.requireUserId();
        Basket basket = baskets.findById(basketId).orElse(null);
        if (basket == null || !basket.getOwnerId().equals(viewerId)) {
            return 0;
        }
        int marked = 0;
        for (BasketRequestEntry request : requests.findByBasketIdOrderByRequestedAt(basketId)) {
            if (request.getStatus() == BasketRequestStatus.REQUESTED) {
                history.record(history.event(BasketAction.REQUEST_STATUS_CHANGED, basketId)
                        .change("status", BasketRequestStatus.REQUESTED, BasketRequestStatus.READ)
                        .metadata("requesterId", request.getRequesterId().toString()).build());
                request.setStatus(BasketRequestStatus.READ);
                marked++;
            }
        }
        return marked;
    }

    /** Account deletion (A40): the user's baskets become DELETED, their requests are removed. */
    @Transactional
    public int deleteAllOf(UUID userId) {
        int count = 0;
        for (Basket basket : baskets.findByOwnerId(userId)) {
            if (basket.getStatus() != BasketStatus.DELETED) {
                history.record(history.event(BasketAction.DELETED, basket.getId()).reason("account deleted")
                        .change("status", basket.getStatus(), BasketStatus.DELETED).build());
                markDeleted(basket);
                count++;
            }
            scrubPersonalData(basket);
        }
        for (BasketRequestEntry request : requests.findByRequesterIdOrderByRequestedAtDesc(userId)) {
            notifications.removeBells(requestBell(request.getBasketId(), userId));
            requests.delete(request);
        }
        return count;
    }

    /** Daily expiry (BasketExpiryJob): ACTIVE baskets past {@code expires_at} become EXPIRED. */
    @Transactional
    public int expire() {
        Instant now = clock.instant();
        List<Basket> due = baskets.findByStatusAndExpiresAtLessThanEqual(BasketStatus.ACTIVE, now);
        for (Basket basket : due) {
            history.record(history.event(BasketAction.EXPIRED, basket.getId())
                    .change("status", BasketStatus.ACTIVE, BasketStatus.EXPIRED).build());
            basket.setStatus(BasketStatus.EXPIRED);
        }
        return due.size();
    }

    /**
     * Account deletion (D17.3): the basket row stays for statistics, without contact data, text and with a home
     * location rounded to about a kilometre.
     */
    private static void scrubPersonalData(Basket basket) {
        basket.setPhone(null);
        basket.setMobile(null);
        basket.setContactPhone(false);
        basket.setContactMobile(false);
        basket.setDescription("");
        if (basket.getLocationType() == BasketLocationType.HOME) {
            basket.setLat(Math.round(basket.getLat() * 100) / 100.0);
            basket.setLon(Math.round(basket.getLon() * 100) / 100.0);
        }
    }

    private void markDeleted(Basket basket) {
        basket.setStatus(BasketStatus.DELETED);
        for (BasketRequestEntry request : requests.findByBasketIdOrderByRequestedAt(basket.getId())) {
            notifications.removeBells(requestBell(basket.getId(), request.getRequesterId()));
        }
        UUID photo = basket.getPhotoUploadId();
        basket.setPhotoUploadId(null);
        deletePhoto(photo);
    }

    private Basket require(UUID basketId) {
        return baskets.findById(basketId).filter(b -> b.getStatus() != BasketStatus.DELETED)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.BASKET_NOT_FOUND));
    }

    private static void locate(Basket basket, User owner, BasketLocationType type, Double lat, Double lon) {
        basket.setLocationType(type);
        if (type == BasketLocationType.HOME) {
            if (owner.getLatitude() == null || owner.getLongitude() == null) {
                throw ApiException.badRequest(ApiErrorCode.BASKET_LOCATION_REQUIRED);
            }
            basket.setLat(owner.getLatitude());
            basket.setLon(owner.getLongitude());
        } else {
            if (!BasketRules.validCoordinates(lat, lon)) {
                throw ApiException.badRequest(ApiErrorCode.BASKET_LOCATION_REQUIRED);
            }
            basket.setLat(lat);
            basket.setLon(lon);
        }
    }

    private void replaceGroceries(UUID basketId, List<UUID> groceryIds) {
        if (groceryIds == null) {
            return;
        }
        Set<UUID> ids = new LinkedHashSet<>(groceryIds);
        if (groceryCatalog.groceryNames(ids).size() != ids.size()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "groceryIds");
        }
        groceries.deleteByBasketId(basketId);
        groceries.flush();
        for (UUID id : ids) {
            BasketGrocery row = new BasketGrocery();
            row.setBasketId(basketId);
            row.setGroceryId(id);
            groceries.save(row);
        }
    }

    private void replaceKinds(UUID basketId, List<BasketKind> values) {
        if (values == null) {
            return;
        }
        kinds.deleteByBasketId(basketId);
        kinds.flush();
        for (BasketKind kind : new LinkedHashSet<>(values)) {
            BasketKindEntry row = new BasketKindEntry();
            row.setBasketId(basketId);
            row.setKind(kind);
            kinds.save(row);
        }
    }

    private void claimPhoto(Basket basket, UUID uploadId, UUID ownerId) {
        Upload upload = uploads.find(uploadId);
        if (!ownerId.equals(upload.getOwnerUserId())) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        uploads.claim(uploadId, new UploadUsage(PHOTO_MODULE, basket.getId()));
        basket.setPhotoUploadId(uploadId);
    }

    private void deletePhoto(UUID uploadId) {
        if (uploadId == null) {
            return;
        }
        try {
            uploads.delete(uploads.find(uploadId));
        } catch (ApiException alreadyGone) {
            // nothing to delete
        }
    }

    private static String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> boolean set(HistoryEvent.Builder event, String field, T current, T next,
                                   java.util.function.Consumer<T> setter) {
        if (next == null || Objects.equals(current, next)) {
            return false;
        }
        event.change(field, "phone".equals(field) || "mobile".equals(field) ? "***" : current,
                "phone".equals(field) || "mobile".equals(field) ? "***" : next);
        setter.accept(next);
        return true;
    }
}
