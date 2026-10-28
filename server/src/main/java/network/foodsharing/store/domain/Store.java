package network.foodsharing.store.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;
import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.HistorySubject;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.Tracked;

/** A store (upstream fs_betrieb). */
@Getter
@Setter
@Entity
@Table(name = "store")
public class Store extends BaseEntity implements HistorySubject {

    @Column(name = "region_id", nullable = false)
    private UUID regionId;

    @Tracked
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Tracked
    @Column(name = "street", length = 200)
    private String street;

    @Tracked
    @Column(name = "postcode", length = 20)
    private String postcode;

    @Tracked
    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Tracked
    @Column(name = "category_id")
    private UUID categoryId;

    @Tracked
    @Column(name = "chain_id")
    private UUID chainId;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "cooperation_status", nullable = false, length = 40)
    private CooperationStatus cooperationStatus;

    @Column(name = "status_date")
    private LocalDate statusDate;

    @Column(name = "cooperation_start")
    private LocalDate cooperationStart;

    @Enumerated(EnumType.STRING)
    @Column(name = "convince_status", nullable = false, length = 40)
    private ConvinceStatus convinceStatus = ConvinceStatus.NOT_SET;

    @Column(name = "contact_person", length = 200)
    private String contactPerson;

    @Tracked(redact = true)
    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_fax", length = 50)
    private String contactFax;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "publicity", nullable = false, length = 20)
    private PublicityStatus publicity = PublicityStatus.NOT_CHOSEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "sticker", nullable = false, length = 20)
    private StickerStatus sticker = StickerStatus.NOT_CHOSEN;

    @Tracked
    @Column(name = "public_info", length = 4000)
    private String publicInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_pickup_time", nullable = false, length = 20)
    private PublicPickupTime publicPickupTime = PublicPickupTime.NOT_SET;

    @Column(name = "extra_slot_allowed", nullable = false)
    private boolean extraSlotAllowed;

    @Tracked
    @Column(name = "weight_category", nullable = false, length = 20)
    private String weightCategory = "NOT_SET";

    @Tracked
    @Column(name = "prefetch_days", nullable = false)
    private int prefetchDays = 14;

    @Tracked
    @Column(name = "use_region_pickup_rule", nullable = false)
    private boolean useRegionPickupRule;

    @Tracked
    @Column(name = "requires_hygiene", nullable = false)
    private boolean requiresHygiene;

    @Tracked
    @Column(name = "requires_verified", nullable = false)
    private boolean requiresVerified = true;

    @Tracked
    @Column(name = "requires_phone", nullable = false)
    private boolean requiresPhone;

    @Tracked
    @Column(name = "requires_application_text", nullable = false)
    private boolean requiresApplicationText;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "team_status", nullable = false, length = 20)
    private TeamSearchStatus teamStatus = TeamSearchStatus.CLOSED;

    @Column(name = "special_notes", length = 32600)
    private String specialNotes;

    @Column(name = "description", length = 32600)
    private String description;

    @Column(name = "team_conversation_id")
    private UUID teamConversationId;

    @Column(name = "jumper_conversation_id")
    private UUID jumperConversationId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Override
    public SubjectType historySubjectType() {
        return SubjectType.STORE;
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
        return StoreLogAction.FIELD_CHANGED;
    }
}
