package network.foodsharing.user.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** History actions of the user module (subject = the user). */
public enum UserAction implements HistoryAction {
    ACCOUNT_CREATED,
    INACTIVITY_WARNED,
    SETTINGS_CHANGED,
    PICKUP_REMINDERS_CHANGED,
    FIELD_CHANGED,
    ROLE_CHANGED,
    DEACTIVATED,
    PROFILE_UPDATED,
    PROFILE_UPDATED_BY_ADMIN,
    ACTIVATED,
    HOME_REGION_CHANGED,
    PHONE_CHANGED,
    // M6 additions (docs/plan-m6.md B1)
    SLEEP_MODE_CHANGED,
    BANANA_GIVEN,
    BANANA_DELETED,
    BUDDY_REQUESTED,
    BUDDY_CONFIRMED,
    BUDDY_REMOVED,
    WALL_POST_CREATED,
    WALL_POST_DELETED,
    NOTE_ADDED,
    NOTE_DELETED,
    EMAIL_CHANGE_REQUESTED,
    EMAIL_CHANGE_COMPLETED,
    EMAIL_CHANGE_ABORTED,
    PHOTO_CHANGED,
    BOUNCE_CLEARED,
    INFO_MAIL_CHANGED,
    ACTIVITY_FILTERS_CHANGED,
    DATA_EXPORTED,
    ACCOUNT_DELETED,
    NO_AUTOMATIC_DELETE_CHANGED,
    BUSINESS_CARD_CREATED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.USER;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.ACCOUNT_LIFETIME;
    }
}
