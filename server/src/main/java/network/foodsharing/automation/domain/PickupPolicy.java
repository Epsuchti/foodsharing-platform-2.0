package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.Tracked;

/** Pickup policy of one store (AU1, A24): rules applied to every unconfirmed sign-up. */
@Getter
@Setter
@Entity
@Table(name = "pickup_policy")
public class PickupPolicy extends BaseEntity implements HistorySubject {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Tracked
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Tracked
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Tracked
    @Column(name = "gap_rule_enabled", nullable = false)
    private boolean gapRuleEnabled;

    @Tracked
    @Column(name = "minimum_gap_days", nullable = false)
    private int minimumGapDays;

    @Tracked
    @Column(name = "cleaning_rule_enabled", nullable = false)
    private boolean cleaningRuleEnabled;

    @Tracked
    @Column(name = "experience_rule_enabled", nullable = false)
    private boolean experienceRuleEnabled;

    @Tracked
    @Column(name = "public_holiday_rule_enabled", nullable = false)
    private boolean publicHolidayRuleEnabled;

    /** Region of the store, set by the service for history (not stored). */
    @Transient
    private UUID regionId;

    @Override
    public SubjectType historySubjectType() {
        return SubjectType.AUTOMATION;
    }

    @Override
    public UUID historySubjectId() {
        return getId();
    }

    @Override
    public UUID historyRegionId() {
        return regionId;
    }

    @Override
    public HistoryAction historyFieldChangedAction() {
        return AutomationAction.FIELD_CHANGED;
    }
}
