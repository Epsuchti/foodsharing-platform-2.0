package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.Tracked;

/** A store chain (key account). */
@Getter
@Setter
@Entity
@Table(name = "store_chain")
public class StoreChain extends BaseEntity implements HistorySubject {

    @Tracked
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "hq_street", length = 200)
    private String hqStreet;

    @Column(name = "hq_postcode", length = 20)
    private String hqPostcode;

    @Column(name = "hq_city", length = 100)
    private String hqCity;

    @Column(name = "hq_country", length = 2)
    private String hqCountry;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private StoreChainStatus status;

    @Column(name = "press_allowed", nullable = false)
    private boolean pressAllowed;

    @Column(name = "forum_thread_id")
    private UUID forumThreadId;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "common_store_information", length = 32600)
    private String commonStoreInformation;

    @Column(name = "estimated_store_count", nullable = false)
    private int estimatedStoreCount;

    @Override
    public SubjectType historySubjectType() {
        return SubjectType.STORE_CHAIN;
    }

    @Override
    public UUID historySubjectId() {
        return getId();
    }

    @Override
    public HistoryAction historyFieldChangedAction() {
        return StoreChainAction.FIELD_CHANGED;
    }
}
