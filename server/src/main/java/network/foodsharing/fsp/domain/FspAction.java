package network.foodsharing.fsp.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Food share point history (subject FSP). */
public enum FspAction implements HistoryAction {
    CREATED,
    UPDATED,
    ACTIVATED,
    DELETED,
    MANAGER_ADDED,
    MANAGER_REMOVED,
    FOLLOWED,
    UNFOLLOWED,
    WALL_POST_CREATED,
    WALL_POST_DELETED,
    FIELD_CHANGED,
    // FS2 cleaning shifts (decision 148)
    CLEANING_ENABLED,
    CLEANING_DISABLED,
    CLEANING_SCHEDULE_CHANGED,
    CLEANING_SIGNED_UP,
    CLEANING_SIGNED_OUT,
    CLEANING_CONFIRMED,
    CLEANING_AUTO_CONFIRM_CHANGED,
    CLEANING_REMINDER_SENT,
    TIME_ZONE_CHANGED,
    CLEANING_REMOVED,
    REGION_CLEANING_FSPS_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.FSP;
    }

    @Override
    public RetentionPolicy retention() {
        // following is a personal preference, not a governance trace (D17.3)
        return this == FOLLOWED || this == UNFOLLOWED ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
