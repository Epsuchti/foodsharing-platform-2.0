package network.foodsharing.pickup.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** User-side pickup events not tied to a store log row (subject = the user). */
public enum PickupAction implements HistoryAction {
    REMINDER_SENT,
    /** An extra slot added by the earliest sign-up (M4 AU6). */
    EXTRA_SLOT_ADDED,
    /** Sign-up confirmed by the pickup policy (actor AUTOMATION). */
    AUTO_CONFIRMED,
    /** Sign-up removed by the pickup policy (actor AUTOMATION). */
    AUTO_DECLINED,
    /** The day chat of a store was opened: found or created, missing signers added (PU3, decision 165). */
    DAY_CONVERSATION_OPENED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.PICKUP;
    }

    @Override
    public RetentionPolicy retention() {
        // pickup sign-up events are kept 2 years (plan §10.1, D17.3)
        return RetentionPolicy.YEARS_2;
    }
}
