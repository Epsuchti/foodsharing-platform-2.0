package network.foodsharing.region.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** History actions of regions (subject = the region; secondary subject = the affected user where there is one). */
public enum RegionAction implements HistoryAction {
    CREATED,
    UPDATED,
    FIELD_CHANGED,
    MOVED,
    DELETED,
    MEMBER_JOINED,
    MEMBER_LEFT,
    MEMBER_REMOVED,
    MEMBER_ADDED_BY_MAINTENANCE,
    ADMIN_GRANTED,
    ADMIN_REVOKED,
    OPTION_CHANGED,
    PIN_CHANGED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    FUNCTION_ASSIGNED,
    FUNCTION_REMOVED,
    SETTINGS_CHANGED,
    STATISTICS_REFRESHED,
    REGIONS_IMPORTED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.REGION;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
