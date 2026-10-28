package network.foodsharing.admin.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** ORGA operations (subject SYSTEM; metadata job name or integration). Codes are prefixed with ADMIN_. */
public enum AdminAction implements HistoryAction {
    JOB_RUN_MANUAL,
    INTEGRATION_PROBED,
    INTEGRATION_TEST_SENT,
    // M6 additions (docs/plan-m6.md B1)
    FEATURE_TOGGLE_CHANGED,
    CONFIGURATION_CHANGED,
    BLOCKLIST_ENTRY_ADDED,
    BLOCKLIST_ENTRY_UPDATED,
    BLOCKLIST_ENTRY_REMOVED,
    IP_BLOCK_ADDED,
    IP_BLOCK_REMOVED,
    UPLOAD_DELETED_BY_ADMIN,
    DONATION_SETTINGS_CHANGED;

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
