package network.foodsharing.admin.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** ORGA operations (subject SYSTEM; metadata job name or integration). Codes are prefixed with ADMIN_. */
public enum AdminAction implements HistoryAction {
    JOB_RUN_MANUAL,
    INTEGRATION_PROBED,
    INTEGRATION_TEST_SENT;

    @Override
    public String code() {
        return "ADMIN_" + name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.SYSTEM;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
