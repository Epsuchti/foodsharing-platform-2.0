package network.foodsharing.calendar.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Calendar feed history (subject CALENDAR = the token owner). Never carries the token. */
public enum CalendarAction implements HistoryAction {
    TOKEN_CREATED,
    TOKEN_DELETED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.CALENDAR;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.DAYS_90;
    }
}
