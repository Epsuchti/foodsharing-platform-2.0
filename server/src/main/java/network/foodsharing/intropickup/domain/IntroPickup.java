package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.SubjectType;

/** An introductory pickup (EinAb) given by a guide (plan §14.1). */
@Getter
@Setter
@Entity
@Table(name = "intro_pickup")
public class IntroPickup extends BaseEntity implements HistorySubject {

    @Column(name = "region_id", nullable = false, updatable = false)
    private UUID regionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private IntroPickupCategory category;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "guide_id", nullable = false)
    private UUID guideId;

    @Column(name = "visit_fairteiler", nullable = false)
    private boolean visitFairteiler;

    @Column(name = "slot_count", nullable = false)
    private int slotCount = 1;

    @Column(name = "minimum_pickup_count")
    private Integer minimumPickupCount;

    @Column(name = "business_name", length = 500)
    private String businessName;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(name = "private_address", length = 500)
    private String privateAddress;

    @Column(name = "public_general_location", length = 500)
    private String publicGeneralLocation;

    @Column(name = "online_call_link", length = 2000)
    private String onlineCallLink;

    @Column(name = "private_info", length = 4000)
    private String privateInfo;

    @Column(name = "public_info", length = 4000)
    private String publicInfo;

    @Column(name = "imported", nullable = false)
    private boolean imported;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isOnline() {
        return category == IntroPickupCategory.ONLINE;
    }

    @Override
    public SubjectType historySubjectType() {
        return SubjectType.INTRO_PICKUP;
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
        return IntroPickupAction.UPDATED;
    }
}
