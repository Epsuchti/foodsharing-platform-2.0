package network.foodsharing.automation.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Automation history (subject AUTOMATION = policy/setting id, metadata storeId; docs/conventions.md A24). */
public enum AutomationAction implements HistoryAction {
    PICKUP_POLICY_CREATED,
    PICKUP_POLICY_UPDATED,
    PICKUP_POLICY_DELETED,
    EXCLUDED_DATE_ADDED,
    EXCLUDED_DATE_REMOVED,
    CLEANING_EXEMPTION_ADDED,
    CLEANING_EXEMPTION_REMOVED,
    PICKUP_DECISION,
    REQUEST_POLICY_UPDATED,
    REQUEST_DECISION,
    ADVERT_UPDATED,
    ADVERT_DELETED,
    ADVERT_SENT,
    LATE_CANCELLATION_HANDLED,
    SIGNOUT_ALERT_UPDATED,
    SIGNOUT_CANCELLATION_RECORDED,
    SIGNOUT_FINAL_NOTIFIED,
    EXTRA_SLOT_ADDED,
    DRY_RUN_EXECUTED,
    AUDITS_ARCHIVED,
    FIELD_CHANGED,
    /** Shared gap list changed (decision 151); metadata storeId, storeIds, minimumGapDays. */
    GAP_LIST_CHANGED,
    /**
     * A sign-up lost its linked-store pickup (decision 167 S3); subject = policy, secondary = person, metadata storeId,
     * pickupAt, linkedStoreId, deadlineAt.
     */
    LINK_MISSING;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.AUTOMATION;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
