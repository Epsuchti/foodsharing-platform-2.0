package network.foodsharing.intropickup.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.api.model.Language;
import network.foodsharing.config.AppProperties;
import network.foodsharing.history.NoHistory;
import network.foodsharing.intropickup.domain.IntroPickup;
import network.foodsharing.intropickup.domain.IntroPickupSlot;
import network.foodsharing.intropickup.domain.IntroPickupSubscription;
import network.foodsharing.intropickup.domain.SlotStatus;
import network.foodsharing.intropickup.domain.SubscriptionChannel;
import network.foodsharing.intropickup.repository.IntroPickupRepository;
import network.foodsharing.intropickup.repository.IntroPickupSlotRepository;
import network.foodsharing.intropickup.repository.IntroPickupSubscriptionRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import network.foodsharing.user.service.UserVerifiedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EinAb bells and mails, all after commit (plan §14.1 EA9): booking confirmations to user and guide, cancellations,
 * updates to booked users, admin deletes to the guide, "new bookable EinAb" to subscribers for whom the booking rules
 * allow it (reference {@code NotificationService.isRelevant}), subscription confirmation, and deactivation of the
 * subscription when the user is verified. Params carry {@code startAt} (ISO) for the client and {@code when}
 * (formatted in the region's time zone) for mails.
 */
@Service
public class IntroPickupNotifications {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final IntroPickupRepository pickups;
    private final IntroPickupSlotRepository slots;
    private final IntroPickupSubscriptionRepository subscriptions;
    private final IntroPickupBookingService booking;
    private final IntroPickupSubscriptionService subscriptionService;
    private final NotificationService notifications;
    private final MailOutboxService mails;
    private final RegionService regions;
    private final UserService users;
    private final AppProperties properties;

    public IntroPickupNotifications(IntroPickupRepository pickups, IntroPickupSlotRepository slots,
                                    IntroPickupSubscriptionRepository subscriptions,
                                    IntroPickupBookingService booking,
                                    IntroPickupSubscriptionService subscriptionService,
                                    NotificationService notifications, MailOutboxService mails, RegionService regions,
                                    UserService users, AppProperties properties) {
        this.pickups = pickups;
        this.slots = slots;
        this.subscriptions = subscriptions;
        this.booking = booking;
        this.subscriptionService = subscriptionService;
        this.notifications = notifications;
        this.mails = mails;
        this.regions = regions;
        this.users = users;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBooked(IntroPickupBookingService.IntroPickupBooked event) {
        pickups.findById(event.introPickupId()).ifPresent(pickup -> {
            String userName = users.findActive(event.userId()).map(User::getDisplayName).orElse("");
            notifications.notify(base(List.of(event.userId()), NotificationType.INTRO_PICKUP_BOOKED, pickup)
                    .link("/intro-pickups/my").mail("intropickup/booked").icon("calendar-check")
                    .param("calendarLink", properties.getBaseUrl() + "/api/intro-pickups/" + pickup.getId()
                            + "/calendar.ics").build());
            notifications.notify(base(List.of(event.guideId()), NotificationType.INTRO_PICKUP_BOOKED_GUIDE, pickup)
                    .param("userName", userName).param("byGuide", String.valueOf(event.byGuide()))
                    .link("/guide/intro-pickups/" + pickup.getId()).mail("intropickup/booked-guide")
                    .icon("user-plus").build());
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelledByUser(IntroPickupBookingService.BookingCancelledByUser event) {
        pickups.findById(event.introPickupId()).ifPresent(pickup -> notifications.notify(
                base(List.of(event.guideId()), NotificationType.INTRO_PICKUP_CANCELLED_BY_USER, pickup)
                        .param("userName", users.findActive(event.userId()).map(User::getDisplayName).orElse(""))
                        .link("/guide/intro-pickups/" + pickup.getId()).mail("intropickup/cancelled-by-user")
                        .icon("user-minus").build()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelledByGuide(IntroPickupGuideService.BookingCancelledByGuide event) {
        pickups.findById(event.introPickupId()).ifPresent(pickup -> notifications.notify(
                base(List.of(event.userId()), NotificationType.INTRO_PICKUP_CANCELLED_BY_GUIDE, pickup)
                        .param("reason", event.reason()).link("/intro-pickups")
                        .mail("intropickup/cancelled-by-guide").icon("calendar-x").build()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdated(IntroPickupGuideService.IntroPickupUpdated event) {
        pickups.findById(event.introPickupId()).ifPresent(pickup -> {
            Set<UUID> booked = new LinkedHashSet<>();
            for (IntroPickupSlot slot : slots.findByPickup(pickup.getId())) {
                if (slot.getStatus() == SlotStatus.BOOKED && slot.getBookingUserId() != null) {
                    booked.add(slot.getBookingUserId());
                }
            }
            if (!booked.isEmpty()) {
                notifications.notify(base(booked, NotificationType.INTRO_PICKUP_UPDATED, pickup)
                        .link("/intro-pickups/my").mail("intropickup/updated").icon("calendar").build());
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDeletedByAdmin(IntroPickupAdminService.IntroPickupDeletedByAdmin event) {
        pickups.findById(event.introPickupId()).ifPresent(pickup -> notifications.notify(
                base(List.of(event.guideId()), NotificationType.INTRO_PICKUP_DELETED_BY_ADMIN, pickup)
                        .param("reason", event.reason()).link("/guide/intro-pickups")
                        .mail("intropickup/deleted-by-admin").icon("trash").build()));
    }

    /** New EinAb: every active subscriber of the region who may book it is told through the chosen channel. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "every bell / mail records NOTIFICATION SENT / MAIL_QUEUED through the notification module")
    public void onCreated(IntroPickupGuideService.IntroPickupCreated event) {
        pickups.findById(event.introPickupId()).filter(p -> p.getDeletedAt() == null)
                .ifPresent(this::notifySubscribers);
    }

    /** @return the number of notified subscribers */
    int notifySubscribers(IntroPickup pickup) {
        int sent = 0;
        for (IntroPickupSubscription subscription : subscriptions.findByRegionIdAndActiveTrue(pickup.getRegionId())) {
            User user = users.findActive(subscription.getUserId()).orElse(null);
            if (user == null || user.isVerified()
                    || booking.notBookableReason(user.getId(), pickup).isPresent()) {
                continue;
            }
            String unsubscribe = properties.getBaseUrl() + "/intro-pickups/unsubscribe?token="
                    + subscription.getUnsubscribeToken();
            if (subscription.getChannel() == SubscriptionChannel.EMAIL && subscription.getDeliveryEmail() != null) {
                Map<String, Object> variables = new HashMap<>(params(pickup));
                variables.put("displayName", user.getDisplayName());
                variables.put("link", properties.getBaseUrl() + "/intro-pickups");
                variables.put("unsubscribeLink", unsubscribe);
                mails.enqueue(new MailDraft(subscription.getDeliveryEmail(), "intropickup/new-bookable",
                        language(subscription.getLanguage()), variables));
            }
            notifications.notify(base(List.of(user.getId()), NotificationType.INTRO_PICKUP_NEW_BOOKABLE, pickup)
                    .link("/intro-pickups").icon("calendar-plus").build());
            sent++;
        }
        return sent;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSubscribed(IntroPickupSubscriptionService.Subscribed event) {
        subscriptions.findById(event.subscriptionId()).ifPresent(subscription -> {
            String region = regions.find(subscription.getRegionId()).map(r -> r.getName()).orElse("");
            notifications.notify(NotificationRequest.to(List.of(subscription.getUserId()),
                            NotificationType.INTRO_PICKUP_SUBSCRIPTION_CONFIRMED).param("regionName", region)
                    .param("channel", subscription.getChannel().name()).link("/intro-pickups").icon("bell").build());
            if (subscription.getChannel() == SubscriptionChannel.EMAIL && subscription.getDeliveryEmail() != null) {
                User user = users.findActive(subscription.getUserId()).orElse(null);
                Map<String, Object> variables = new HashMap<>();
                variables.put("displayName", user == null ? "" : user.getDisplayName());
                variables.put("regionName", region);
                variables.put("link", properties.getBaseUrl() + "/intro-pickups");
                variables.put("unsubscribeLink", properties.getBaseUrl() + "/intro-pickups/unsubscribe?token="
                        + subscription.getUnsubscribeToken());
                mails.enqueue(new MailDraft(subscription.getDeliveryEmail(), "intropickup/subscription-confirmed",
                        language(subscription.getLanguage()), variables));
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @NoHistory(reason = "the subscription service records UNSUBSCRIBED when it deactivates")
    public void onUserVerified(UserVerifiedEvent event) {
        subscriptionService.deactivateForVerifiedUser(event.userId());
    }

    private NotificationRequest.Builder base(java.util.Collection<UUID> recipients, NotificationType type,
                                             IntroPickup pickup) {
        NotificationRequest.Builder builder = NotificationRequest.to(recipients, type);
        params(pickup).forEach(builder::param);
        return builder;
    }

    private Map<String, String> params(IntroPickup pickup) {
        ZoneId zone = regions.zoneOf(pickup.getRegionId());
        Map<String, String> params = new HashMap<>();
        params.put("introPickupId", pickup.getId().toString());
        params.put("category", pickup.getCategory().name());
        params.put("startAt", pickup.getStartAt().toString());
        params.put("when", WHEN.format(pickup.getStartAt().atZone(zone)));
        params.put("businessName", pickup.getBusinessName() == null ? "Online" : pickup.getBusinessName());
        return params;
    }

    private static Language language(String value) {
        try {
            return Language.fromValue(value);
        } catch (IllegalArgumentException e) {
            return Language.DE;
        }
    }
}
