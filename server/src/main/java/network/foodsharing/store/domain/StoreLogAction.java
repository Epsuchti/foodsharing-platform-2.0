package network.foodsharing.store.domain;

import java.util.Set;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Store log (subject = the store; secondary subject = the affected user; A21). */
public enum StoreLogAction implements HistoryAction {
    REQUEST_TO_JOIN,
    REQUEST_DECLINED,
    REQUEST_APPROVED,
    ADDED_WITHOUT_REQUEST,
    MOVED_TO_JUMPER,
    MOVED_TO_TEAM,
    REMOVED_FROM_STORE,
    LEFT_STORE,
    APPOINT_STORE_MANAGER,
    REMOVED_AS_STORE_MANAGER,
    SIGN_UP_SLOT,
    SIGN_OUT_SLOT,
    REMOVED_FROM_SLOT,
    SLOT_CONFIRMED,
    DELETED_FROM_WALL,
    REQUEST_CANCELLED,
    INVITED_TO_TEAM,
    INVITATION_WITHDRAWN,
    INVITATION_ACCEPTED,
    INVITATION_DECLINED,
    DELETE_STORE,
    UPDATE_SLOT_COUNT,
    UPDATE_SLOT_DESCRIPTION,
    REGION_PICKUP_RULE_ENABLED,
    REGION_PICKUP_RULE_DISABLED,
    UPDATE_REGULAR_PICKUP_SETTINGS,
    UPDATE_REGULAR_PICKUP_INTERVAL,
    STORE_CREATED,
    STORE_UPDATED,
    FIELD_CHANGED,
    MOVED_TO_REGION,
    TEAM_STATUS_CHANGED,
    COOPERATION_STATUS_CHANGED,
    WALL_POST_ADDED,
    ALL_FUTURE_PICKUPS_DELETED;

    private static final Set<String> SHORT_LIVED = Set.of("SIGN_UP_SLOT", "SIGN_OUT_SLOT", "REMOVED_FROM_SLOT", "SLOT_CONFIRMED", "UPDATE_SLOT_COUNT", "UPDATE_SLOT_DESCRIPTION");

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.STORE;
    }

    @Override
    public RetentionPolicy retention() {
        return SHORT_LIVED.contains(name()) ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
