package network.foodsharing.report.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Report history (subject REPORT; the report log D10.3). */
public enum ReportAction implements HistoryAction {
    CREATED,
    STATUS_CHANGED,
    CONSEQUENCE_SET,
    REMINDER_SET,
    THREAD_LINKED,
    DELETED,
    WALL_POST_CREATED,
    WALL_POST_DELETED,
    MEDIATION_REQUESTED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.REPORT;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.FOREVER;
    }
}
