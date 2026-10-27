package network.foodsharing.intropickup.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** EinAb history (subject = the EinAb; secondary subject = the booking user). */
public enum IntroPickupAction implements HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    DELETED_BY_ADMIN,
    GUIDE_ASSIGNED,
    SLOT_BOOKED,
    SLOT_BOOKED_BY_GUIDE,
    BOOKING_CANCELLED_BY_USER,
    BOOKING_CANCELLED_BY_GUIDE,
    MARKED_DONE,
    COUNTED,
    NOT_COUNTED,
    COMMENT_ADDED,
    HISTORICAL_CREATED,
    IMPORTED,
    TEMPLATE_SAVED,
    TEMPLATE_DELETED,
    GUIDE_PROFILE_UPDATED,
    GUIDE_ENABLED,
    GUIDE_DISABLED,
    SUBSCRIBED,
    UNSUBSCRIBED,
    STORE_ALIAS_CHANGED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.INTRO_PICKUP;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
