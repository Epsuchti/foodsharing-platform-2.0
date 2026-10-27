package network.foodsharing.pickup.domain;

import java.util.Set;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** User-side pickup events not tied to a store log row (subject = the user). */
public enum PickupAction implements HistoryAction {
    REMINDER_SENT;

    private static final Set<String> SHORT_LIVED = Set.of("REMINDER_SENT");

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
        return SHORT_LIVED.contains(name()) ? RetentionPolicy.YEARS_2 : RetentionPolicy.FOREVER;
    }
}
