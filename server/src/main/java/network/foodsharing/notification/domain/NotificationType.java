package network.foodsharing.notification.domain;

import static network.foodsharing.notification.domain.NotificationCategory.AUTOMATION;
import static network.foodsharing.notification.domain.NotificationCategory.FORUM;
import static network.foodsharing.notification.domain.NotificationCategory.INTRO_PICKUPS;
import static network.foodsharing.notification.domain.NotificationCategory.MESSAGES;
import static network.foodsharing.notification.domain.NotificationCategory.ONBOARDING;
import static network.foodsharing.notification.domain.NotificationCategory.PICKUPS;
import static network.foodsharing.notification.domain.NotificationCategory.REGION;
import static network.foodsharing.notification.domain.NotificationCategory.STORE_TEAM;
import static network.foodsharing.notification.domain.NotificationCategory.SYSTEM;

/**
 * What a notification is about (bell type, mail choice, channel settings via {@link #category()}). M4 declares every
 * type up front, including the M5/M6 ones (upstream bell list,
    // M6 additions (docs/plan-m6.md B1)
    FSP_NEW_POST(REGION, "map-pin"),
    MEDIATION_REQUEST(REGION, "scale"),
    ACHIEVEMENT_AWARDED(SYSTEM, "award"),
    EMAIL_CHANGE_REQUESTED(SYSTEM, "mail"),
    SUPPORT_TICKET(SYSTEM, "life-buoy"),
    BASKET_REQUEST_ANSWERED(MESSAGES, "shopping-basket"); texts exist, emitted later). A task that needs another
 * one uses {@link #GENERIC} with a {@code titleKey} parameter. {@link #icon()} is the default bell icon (Lucide name).
 */
public enum NotificationType {
    GENERIC(SYSTEM, "bell"),
    REGION_NEW_MEMBER(REGION, "user-plus"),
    GROUP_APPLICATION_RECEIVED(REGION, "user-plus"),
    GROUP_APPLICATION_ACCEPTED(REGION, "users"),
    GROUP_APPLICATION_DECLINED(REGION, "user-minus"),
    GROUP_MAIL(REGION, "mail"),
    INTRO_PICKUP_BOOKED(INTRO_PICKUPS, "calendar-check"),
    INTRO_PICKUP_BOOKED_GUIDE(INTRO_PICKUPS, "calendar-plus"),
    INTRO_PICKUP_CANCELLED_BY_GUIDE(INTRO_PICKUPS, "calendar-x"),
    INTRO_PICKUP_CANCELLED_BY_USER(INTRO_PICKUPS, "calendar-x"),
    INTRO_PICKUP_DELETED_BY_ADMIN(INTRO_PICKUPS, "trash"),
    INTRO_PICKUP_NEW_BOOKABLE(INTRO_PICKUPS, "calendar"),
    INTRO_PICKUP_SUBSCRIPTION_CONFIRMED(INTRO_PICKUPS, "calendar"),
    INTRO_PICKUP_UPDATED(INTRO_PICKUPS, "calendar"),
    STORE_NEW_REQUEST(STORE_TEAM, "user-plus"),
    STORE_REQUEST_ACCEPTED(STORE_TEAM, "store"),
    STORE_REQUEST_DECLINED(STORE_TEAM, "store"),
    STORE_INVITATION(STORE_TEAM, "store"),
    STORE_INVITATION_ACCEPTED(STORE_TEAM, "user-plus"),
    STORE_INVITATION_DECLINED(STORE_TEAM, "user-minus"),
    STORE_MEMBER_REMOVED(STORE_TEAM, "user-minus"),
    STORE_MOVED_TO_STANDBY(STORE_TEAM, "store"),
    STORE_MANAGER_APPOINTED(STORE_TEAM, "badge-check"),
    STORE_MANAGER_REMOVED(STORE_TEAM, "store"),
    STORE_DELETED(STORE_TEAM, "trash"),
    STORE_REGULAR_PICKUP_CHANGED(PICKUPS, "calendar"),
    STORE_TEAM_REOPENED(STORE_TEAM, "store"),
    PICKUP_SIGNUP_UNCONFIRMED(PICKUPS, "calendar-plus"),
    PICKUP_CONFIRMED(PICKUPS, "calendar-check"),
    PICKUP_REMOVED(PICKUPS, "calendar-x"),
    PICKUP_REMINDER(NotificationCategory.PICKUP_REMINDER, "alarm-clock"),
    USER_VERIFIED(SYSTEM, "badge-check"),
    PASSPORT_CREATED(SYSTEM, "id-card"),
    QUIZ_PASSED(ONBOARDING, "graduation-cap"),
    ONBOARDING_STALLED(ONBOARDING, "compass"),
    ONBOARDING_STEP_UNBLOCKED(ONBOARDING, "unlock"),
    HYGIENE_EXPIRING(SYSTEM, "shield"),
    // --- M4 ---
    PICKUP_AUTO_CONFIRMED_WITH_MESSAGE(AUTOMATION, "calendar-check"),
    PICKUP_AUTO_DECLINED(AUTOMATION, "calendar-x"),
    STORE_REQUEST_AUTO_ACCEPTED(AUTOMATION, "store"),
    STORE_REQUEST_AUTO_DECLINED(AUTOMATION, "store"),
    PICKUP_OPEN_SLOT(PICKUPS, "calendar-plus"),
    PICKUP_LATE_CANCELLATION(PICKUPS, "calendar-x"),
    PICKUP_SIGNOUT_ALERT(PICKUPS, "calendar-x"),
    PICKUP_SIGNOUT_NOT_REPLACED(PICKUPS, "calendar-x"),
    PICKUP_EXTRA_SLOT_ADDED(PICKUPS, "calendar-plus"),
    PICKUP_UNCONFIRMED_REMINDER(PICKUPS, "alarm-clock"),
    PICKUP_EMPTY_TOMORROW(PICKUPS, "alarm-clock"),
    STORE_MANAGER_INACTIVE(STORE_TEAM, "store"),
    AUTOMATION_RUN_FAILED(AUTOMATION, "bot"),
    TELEGRAM_LINKED(SYSTEM, "send"),
    NEWSLETTER(SYSTEM, "newspaper"),
    INTEGRATION_DOWN(SYSTEM, "plug-zap"),
    // --- declared for M5/M6 ---
    FORUM_THREAD_TO_ACTIVATE(FORUM, "message-square"),
    FORUM_NEW_THREAD(FORUM, "message-square"),
    FORUM_POST(FORUM, "message-square"),
    FORUM_MENTION(FORUM, "at-sign"),
    POST_HIDDEN(FORUM, "eye-off"),
    EVENT_POST(REGION, "calendar"),
    POLL_NEW(REGION, "vote"),
    POLL_STARTED(REGION, "vote"),
    POLL_ENDING_SOON(REGION, "vote"),
    BASKET_REQUEST(MESSAGES, "shopping-basket"),
    BASKET_REQUEST_ANSWERED(MESSAGES, "shopping-basket"),
    BUDDY_REQUEST(MESSAGES, "user-plus"),
    BANANA_GIVEN(MESSAGES, "heart"),
    REPORT_NEW(REGION, "flag"),
    REPORT_REMINDER(REGION, "flag"),
    FSP_ACTIVATE(REGION, "map-pin"),
    STORE_WALL_POST(STORE_TEAM, "message-square"),
    STORE_NEW_IN_REGION(REGION, "store"),
    STORE_ADDED_TO_CHAIN(STORE_TEAM, "store"),
    CHAIN_INFO_UPDATED(STORE_TEAM, "store"),
    BLOG_NEW_CHECK(REGION, "newspaper"),
    NEW_RESOURCE(REGION, "book-open"),
    QUIZ_COMMENT(ONBOARDING, "graduation-cap"),
    ADDRESS_CHANGED(SYSTEM, "map-pin"),
    NEW_MESSAGE(MESSAGES, "mail"),
    // M5 additions (docs/plan-m5.md B1)
    MAILBOX_NEW_MAIL(NotificationCategory.MAILBOX, "mail"),
    EVENT_INVITATION(REGION, "calendar"),
    EVENT_UPDATED(REGION, "calendar"),
    POLL_CANCELLED(REGION, "vote"),
    SURVEY_SHARED(REGION, "clipboard-list");

    private final NotificationCategory category;
    private final String icon;

    NotificationType(NotificationCategory category, String icon) {
        this.category = category;
        this.icon = icon;
    }

    public NotificationCategory category() {
        return category;
    }

    public String icon() {
        return icon;
    }

    /** i18n key of the bell title: {@code bell.<type>} (GENERIC: the {@code titleKey} parameter). */
    public String defaultTitleKey() {
        return "bell." + name();
    }
}
