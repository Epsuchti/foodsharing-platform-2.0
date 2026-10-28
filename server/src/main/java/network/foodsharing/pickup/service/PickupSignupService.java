package network.foodsharing.pickup.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.service.BellService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.pickup.domain.PickupAction;
import network.foodsharing.pickup.domain.PickupDate;
import network.foodsharing.pickup.domain.PickupEvents;
import network.foodsharing.pickup.domain.PickupSignup;
import network.foodsharing.pickup.repository.PickupDateRepository;
import network.foodsharing.pickup.repository.PickupSignupRepository;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import network.foodsharing.store.domain.UserTeamStatus;
import network.foodsharing.store.service.StoreNotifications;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up, sign-out, confirmation and single-pickup edits (PU1–PU5, A20). Sign-ups are serialised per store by a row
 * lock on the store; the first failing eligibility reason answers 409 PICKUP_NOT_ELIGIBLE (args.reason). Every action
 * is a store-log entry with {@code metadata.pickupAt}; bells go out after commit.
 */
@Service
public class PickupSignupService {

    private final PickupScheduleService schedule;
    private final PickupDateRepository dates;
    private final PickupSignupRepository signups;
    private final StoreService stores;
    private final StorePermissionService rights;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    public PickupSignupService(PickupScheduleService schedule, PickupDateRepository dates,
                               PickupSignupRepository signups, StoreService stores, StorePermissionService rights,
                               UserService users, CurrentActorService currentActor, HistoryService history,
                               ApplicationEventPublisher events, AppProperties properties, Clock clock) {
        this.schedule = schedule;
        this.dates = dates;
        this.signups = signups;
        this.stores = stores;
        this.rights = rights;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Signs the current user up; managers (COORDINATOR) are pre-confirmed, otherwise the managers get a grouped
     * PICKUP_SIGNUP_UNCONFIRMED bell. A regular occurrence becomes a {@code pickup} row (A20).
     *
     * @return whether the sign-up is confirmed
     * @throws ApiException 404, 403 unless maySeePickups, 409 PICKUP_NOT_ELIGIBLE (args.reason)
     */
    @Transactional
    public boolean signUp(UUID storeId, Instant pickupAt) {
        stores.require(storeId);
        Store store = stores.lock(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).maySeePickups());
        PickupSchedule.Slot slot = schedule.find(store, pickupAt).orElse(null);
        PickupEligibilityRules.Result result = schedule.context(store, actor.userId()).evaluate(pickupAt, slot);
        if (!result.eligible()) {
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, ApiErrorCode.PICKUP_NOT_ELIGIBLE,
                    List.of(), Map.of("reason", result.reasons().getFirst().name()));
        }
        if (dates.findByStoreIdAndPickupAt(storeId, pickupAt).isEmpty()) {
            PickupDate date = new PickupDate();
            date.setStoreId(storeId);
            date.setPickupAt(pickupAt);
            date.setSlotCount(slot.slotCount());
            date.setDescription(slot.description());
            dates.save(date);
        }
        boolean confirmed = rights.teamStatus(actor.userId(), storeId) == UserTeamStatus.COORDINATOR;
        PickupSignup signup = new PickupSignup();
        signup.setStoreId(storeId);
        signup.setUserId(actor.userId());
        signup.setPickupAt(pickupAt);
        signup.setConfirmed(confirmed);
        if (confirmed) {
            signup.setConfirmedBy(actor.userId());
            signup.setConfirmedAt(clock.instant());
        }
        signups.save(signup);
        log(StoreLogAction.SIGN_UP_SLOT, store, actor.userId(), pickupAt, null, Map.of());
        events.publishEvent(new PickupEvents.PickupSignedUpEvent(storeId, pickupAt, actor.userId(), confirmed,
                clock.instant()));
        if (!confirmed) {
            List<UUID> managers = stores.managerIds(storeId).stream().filter(id -> !id.equals(actor.userId())).toList();
            StoreNotifications.send(events, request(managers, NotificationType.PICKUP_SIGNUP_UNCONFIRMED, store,
                    pickupAt).param("userName", users.findActive(actor.userId()).map(User::getDisplayName).orElse(""))
                    .identifier(BellService.identifier(NotificationType.PICKUP_SIGNUP_UNCONFIRMED, storeId))
                    .build());
        }
        return confirmed;
    }

    /**
     * Self sign-out ({@code SIGN_OUT_SLOT}) or removal by a store editor / user administrator ({@code
     * REMOVED_FROM_SLOT}, bell + mail PICKUP_REMOVED with the optional message).
     *
     * @throws ApiException 404 / SIGNUP_NOT_FOUND, 403, 400 PICKUP_IN_PAST
     */
    @Transactional
    public void signOut(UUID storeId, Instant pickupAt, UUID userId, String message, boolean sendMessage) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.mayRemovePickupUser(actor, store, userId));
        if (pickupAt.isBefore(clock.instant())) {
            throw ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST);
        }
        PickupSignup signup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        signups.delete(signup);
        boolean self = actor.userId().equals(userId);
        String text = message == null || message.isBlank() ? null : message.strip();
        log(self ? StoreLogAction.SIGN_OUT_SLOT : StoreLogAction.REMOVED_FROM_SLOT, store, userId, pickupAt,
                self ? null : text, Map.of());
        events.publishEvent(new PickupEvents.PickupSignedOutEvent(storeId, pickupAt, userId, actor.userId(),
                clock.instant()));
        if (!self) {
            NotificationRequest.Builder bell = request(List.of(userId), NotificationType.PICKUP_REMOVED, store,
                    pickupAt).param("message", sendMessage ? text : null);
            StoreNotifications.send(events, sendMessage ? bell.mail("pickup/removed").build() : bell.build());
        }
    }

    /** @throws ApiException 404 / SIGNUP_NOT_FOUND, 403 unless mayConfirmPickups, 409 ALREADY_CONFIRMED */
    @Transactional
    public void confirm(UUID storeId, Instant pickupAt, UUID userId) {
        Store store = stores.require(storeId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(rights.rights(actor, store).mayConfirmPickups());
        PickupSignup signup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.SIGNUP_NOT_FOUND));
        if (signup.isConfirmed()) {
            throw ApiException.conflict(ApiErrorCode.ALREADY_CONFIRMED);
        }
        signup.setConfirmed(true);
        signup.setConfirmedBy(actor.userId());
        signup.setConfirmedAt(clock.instant());
        log(StoreLogAction.SLOT_CONFIRMED, store, userId, pickupAt, null, Map.of());
        StoreNotifications.send(events, request(List.of(userId), NotificationType.PICKUP_CONFIRMED, store, pickupAt)
                .build());
        events.publishEvent(new PickupEvents.PickupConfirmedEvent(storeId, pickupAt, userId, actor.userId()));
        removeUnconfirmedBellWhenDone(storeId);
    }

    /**
     * A24: the pickup policy confirms a sign-up (no permission check; actor AUTOMATION {@code policyName}). Store log
     * SLOT_CONFIRMED + {@link PickupAction#AUTO_CONFIRMED}; bell PICKUP_CONFIRMED, or bell + mail
     * PICKUP_AUTO_CONFIRMED_WITH_MESSAGE when a message is given (e.g. the cleaning reminder).
     *
     * @return false when the sign-up is gone or already confirmed (nothing happens)
     */
    @Transactional
    public boolean confirmByAutomation(UUID storeId, Instant pickupAt, UUID userId, String policyName,
                                       String message) {
        Store store = stores.require(storeId);
        PickupSignup signup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt).orElse(null);
        if (signup == null || signup.isConfirmed()) {
            return false;
        }
        signup.setConfirmed(true);
        signup.setConfirmedBy(null);
        signup.setConfirmedAt(clock.instant());
        String text = message == null || message.isBlank() ? null : message.strip();
        logAutomation(StoreLogAction.SLOT_CONFIRMED, store, userId, pickupAt, null, policyName);
        history.record(history.event(PickupAction.AUTO_CONFIRMED, userId).actorAutomation(policyName)
                .region(store.getRegionId()).metadata("storeId", storeId.toString())
                .metadata("pickupAt", pickupAt.toString()).build());
        NotificationRequest.Builder bell = text == null
                ? request(List.of(userId), NotificationType.PICKUP_CONFIRMED, store, pickupAt)
                : request(List.of(userId), NotificationType.PICKUP_AUTO_CONFIRMED_WITH_MESSAGE, store, pickupAt)
                        .param("message", text).mail("pickup/auto-confirmed");
        StoreNotifications.send(events, bell.build());
        events.publishEvent(new PickupEvents.PickupConfirmedEvent(storeId, pickupAt, userId, null));
        removeUnconfirmedBellWhenDone(storeId);
        return true;
    }

    /**
     * A24: the pickup policy removes a sign-up (no permission check, also within the pickup's last hours). Store log
     * REMOVED_FROM_SLOT (reason = message) + {@link PickupAction#AUTO_DECLINED}; bell + mail PICKUP_AUTO_DECLINED.
     *
     * @return false when the sign-up is gone
     */
    @Transactional
    public boolean removeByAutomation(UUID storeId, Instant pickupAt, UUID userId, String policyName,
                                      String message) {
        Store store = stores.require(storeId);
        PickupSignup signup = signups.findByStoreIdAndUserIdAndPickupAt(storeId, userId, pickupAt).orElse(null);
        if (signup == null) {
            return false;
        }
        signups.delete(signup);
        String text = message == null || message.isBlank() ? null : message.strip();
        logAutomation(StoreLogAction.REMOVED_FROM_SLOT, store, userId, pickupAt, text, policyName);
        history.record(history.event(PickupAction.AUTO_DECLINED, userId).actorAutomation(policyName)
                .region(store.getRegionId()).metadata("storeId", storeId.toString())
                .metadata("pickupAt", pickupAt.toString()).reason(text).build());
        StoreNotifications.send(events, request(List.of(userId), NotificationType.PICKUP_AUTO_DECLINED, store,
                pickupAt).param("reason", text).mail("pickup/auto-declined").build());
        events.publishEvent(new PickupEvents.PickupSignedOutEvent(storeId, pickupAt, userId, null, clock.instant()));
        removeUnconfirmedBellWhenDone(storeId);
        return true;
    }

    private void removeUnconfirmedBellWhenDone(UUID storeId) {
        signups.flush();
        if (!signups.existsByStoreIdAndConfirmedFalseAndPickupAtAfter(storeId, clock.instant())) {
            StoreNotifications.removeBells(events,
                    BellService.identifier(NotificationType.PICKUP_SIGNUP_UNCONFIRMED, storeId));
        }
    }

    private void logAutomation(StoreLogAction action, Store store, UUID userId, Instant pickupAt, String reason,
                               String policyName) {
        history.record(history.event(action, store.getId()).actorAutomation(policyName).secondarySubject(userId)
                .region(store.getRegionId()).metadata("pickupAt", pickupAt.toString()).reason(reason).build());
    }

    /**
     * Sets slot count / description of one future pickup; creates the one-time row when there is none (also adds a
     * pickup at a time without occurrence). 0 slots cancel it.
     *
     * @throws ApiException 404, 403 unless mayEditPickups, 400 PICKUP_IN_PAST / SLOT_COUNT_OUT_OF_RANGE /
     *                      DESCRIPTION_TOO_LONG, 409 MORE_OCCUPIED_SLOTS
     */
    @Transactional
    public PickupSchedule.Slot edit(UUID storeId, Instant pickupAt, int slotCount, String description) {
        stores.require(storeId);
        Store store = stores.lock(storeId);
        StorePermissionService.require(rights.rights(currentActor.requirePrincipal(), store).mayEditPickups());
        if (pickupAt.isBefore(clock.instant())) {
            throw ApiException.badRequest(ApiErrorCode.PICKUP_IN_PAST);
        }
        if (slotCount < 0 || slotCount > properties.getStore().getMaxSlotsPerPickup()) {
            throw ApiException.badRequest(ApiErrorCode.SLOT_COUNT_OUT_OF_RANGE);
        }
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > 100) {
            throw ApiException.badRequest(ApiErrorCode.DESCRIPTION_TOO_LONG);
        }
        PickupSchedule.Slot current = schedule.find(store, pickupAt).orElse(null);
        if (current != null && current.occupied() > slotCount) {
            throw ApiException.conflict(ApiErrorCode.MORE_OCCUPIED_SLOTS);
        }
        PickupDate date = dates.findByStoreIdAndPickupAt(storeId, pickupAt).orElseGet(() -> {
            PickupDate d = new PickupDate();
            d.setStoreId(storeId);
            d.setPickupAt(pickupAt);
            return d;
        });
        int previousCount = current == null ? 0 : current.slotCount();
        String previousDescription = current == null ? null : current.description();
        date.setSlotCount(slotCount);
        date.setDescription(text);
        dates.save(date);
        if (current == null || previousCount != slotCount) {
            log(StoreLogAction.UPDATE_SLOT_COUNT, store, null, pickupAt, null,
                    Map.of("previousCount", previousCount, "newCount", slotCount));
        }
        if (!Objects.equals(previousDescription, text)) {
            log(StoreLogAction.UPDATE_SLOT_DESCRIPTION, store, null, pickupAt, null,
                    text == null ? Map.of() : Map.of("description", text));
        }
        return schedule.find(store, pickupAt).orElseGet(() -> new PickupSchedule.Slot(storeId, pickupAt, 0, text,
                current != null && current.regular(), List.of()));
    }

    private NotificationRequest.Builder request(List<UUID> recipients, NotificationType type, Store store,
                                                Instant pickupAt) {
        return NotificationRequest.to(recipients, type).param("storeId", store.getId().toString())
                .param("storeName", store.getName()).param("pickupAt", pickupAt.toString())
                .param("when", PickupFormat.when(pickupAt, schedule.zoneOf(store)))
                .link("/stores/" + store.getId() + "/pickups").icon("calendar");
    }

    private void log(StoreLogAction action, Store store, UUID userId, Instant pickupAt, String reason,
                     Map<String, ?> metadata) {
        history.record(history.event(action, store.getId()).secondarySubject(userId).region(store.getRegionId())
                .metadata("pickupAt", pickupAt.toString()).metadata(metadata).reason(reason).build());
    }
}
