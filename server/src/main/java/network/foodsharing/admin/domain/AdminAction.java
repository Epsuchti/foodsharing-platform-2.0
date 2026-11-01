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
    DONATION_SETTINGS_CHANGED,
    // D17.2: automatic blocks of the API rate limit; they hold raw IPs or user ids, so they are kept 90 days
    RATE_LIMIT_BLOCKED,
    RATE_LIMIT_BLOCK_LIFTED;

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
        // no constant-specific bodies: they would make getClass() an anonymous subclass (contract checks use its name)
        return switch (this) {
            case RATE_LIMIT_BLOCKED, RATE_LIMIT_BLOCK_LIFTED -> RetentionPolicy.DAYS_90;
            default -> RetentionPolicy.YEARS_2;
        };
    }
}
