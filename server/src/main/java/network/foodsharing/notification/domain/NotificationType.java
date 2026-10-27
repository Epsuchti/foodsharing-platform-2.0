package network.foodsharing.notification.domain;

/**
 * What a notification is about (bell type, mail choice). All M2 types are declared here up front (docs/plan-m2.md
 * Part B); a task that needs another one uses {@link #GENERIC} with a {@code titleKey} parameter.
 */
public enum NotificationType {
    GENERIC,
    REGION_NEW_MEMBER,
    GROUP_APPLICATION_RECEIVED,
    GROUP_APPLICATION_ACCEPTED,
    GROUP_APPLICATION_DECLINED,
    GROUP_MAIL,
    INTRO_PICKUP_BOOKED,
    INTRO_PICKUP_BOOKED_GUIDE,
    INTRO_PICKUP_CANCELLED_BY_GUIDE,
    INTRO_PICKUP_CANCELLED_BY_USER,
    INTRO_PICKUP_DELETED_BY_ADMIN,
    INTRO_PICKUP_NEW_BOOKABLE,
    INTRO_PICKUP_SUBSCRIPTION_CONFIRMED,
    INTRO_PICKUP_UPDATED,
    STORE_NEW_REQUEST,
    STORE_REQUEST_ACCEPTED,
    STORE_REQUEST_DECLINED,
    STORE_INVITATION,
    STORE_INVITATION_ACCEPTED,
    STORE_INVITATION_DECLINED,
    STORE_MEMBER_REMOVED,
    STORE_MOVED_TO_STANDBY,
    STORE_MANAGER_APPOINTED,
    STORE_MANAGER_REMOVED,
    STORE_DELETED,
    STORE_REGULAR_PICKUP_CHANGED,
    STORE_TEAM_REOPENED,
    PICKUP_SIGNUP_UNCONFIRMED,
    PICKUP_CONFIRMED,
    PICKUP_REMOVED,
    PICKUP_REMINDER,
    USER_VERIFIED,
    PASSPORT_CREATED,
    QUIZ_PASSED,
    ONBOARDING_STALLED,
    ONBOARDING_STEP_UNBLOCKED,
    HYGIENE_EXPIRING;

    /** i18n key of the bell title: {@code bell.<type>} (GENERIC: the {@code titleKey} parameter). */
    public String defaultTitleKey() {
        return "bell." + name();
    }
}
