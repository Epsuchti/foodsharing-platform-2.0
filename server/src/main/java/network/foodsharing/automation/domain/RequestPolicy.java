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

/** Request policy of one store (AU2): accepts team applications automatically, optionally by distance. */
@Getter
@Setter
@Entity
@Table(name = "request_policy")
public class RequestPolicy extends BaseEntity implements HistorySubject {

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Tracked
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Tracked
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = true;

    @Tracked
    @Column(name = "distance_rule_enabled", nullable = false)
    private boolean distanceRuleEnabled;

    @Tracked
    @Column(name = "maximum_distance_km", nullable = false)
    private double maximumDistanceKm;

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
