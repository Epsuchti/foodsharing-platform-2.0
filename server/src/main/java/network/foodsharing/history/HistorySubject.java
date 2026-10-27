package network.foodsharing.history;

import java.util.UUID;

/** Entities with {@link Tracked} fields implement this so the listener knows what the change is about. */
public interface HistorySubject {

    SubjectType historySubjectType();

    UUID historySubjectId();

    /** Region the subject belongs to (for region-scoped history views); null when none. */
    default UUID historyRegionId() {
        return null;
    }

    /** The module's {@code FIELD_CHANGED} action. */
    HistoryAction historyFieldChangedAction();
}
