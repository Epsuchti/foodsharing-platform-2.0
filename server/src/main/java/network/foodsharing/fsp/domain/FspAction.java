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
    FIELD_CHANGED;

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
        return RetentionPolicy.FOREVER;
    }
}
