package network.foodsharing.basket.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Food basket history (subject BASKET). */
public enum BasketAction implements HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    EXPIRED,
    REQUESTED,
    REQUEST_WITHDRAWN,
    REQUEST_STATUS_CHANGED,
    FIELD_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.BASKET;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
