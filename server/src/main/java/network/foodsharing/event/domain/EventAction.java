package network.foodsharing.event.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Event history (subject EVENT). */
public enum EventAction implements HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    INVITATIONS_SENT,
    RSVP_CHANGED,
    WALL_POST_CREATED,
    WALL_POST_DELETED,
    FIELD_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.EVENT;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
