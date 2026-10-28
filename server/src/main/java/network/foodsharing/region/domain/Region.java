package network.foodsharing.region.domain;

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

/** A region or working group (plan §15.3); working groups have type WORKING_GROUP and their parent region as parent. */
@Getter
@Setter
@Entity
@Table(name = "region")
public class Region extends BaseEntity implements HistorySubject {

    @Tracked
    @Column(name = "parent_id")
    private UUID parentId;

    @Tracked
    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Tracked
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private UnitType type;

    @Tracked
    @Column(name = "teaser", length = 2000)
    private String teaser;

    @Tracked
    @Column(name = "description", length = 32600)
    private String description;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Tracked
    @Column(name = "email_name", length = 100)
    private String emailName;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "Europe/Zurich";

    @Column(name = "country", length = 2)
    private String country;

    @Tracked
    @Enumerated(EnumType.STRING)
    @Column(name = "join_mode", nullable = false, length = 20)
    private RegionJoinMode joinMode = RegionJoinMode.OPEN;

    @Column(name = "moderated", nullable = false)
    private boolean moderated;

    /** A33: members of the region/group share a conversation (default on for working groups). */
    @Column(name = "conversation_enabled", nullable = false)
    private boolean conversationEnabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "has_children", nullable = false)
    private boolean hasChildren;

    @Column(name = "conference_url", length = 500)
    private String conferenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "apply_type", length = 20)
    private ApplyType applyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_category", length = 20)
    private GroupCategory groupCategory;

    @Column(name = "min_pickups", nullable = false)
    private int minPickups;

    @Column(name = "min_bananas", nullable = false)
    private int minBananas;

    @Column(name = "min_weeks", nullable = false)
    private int minWeeks;

    @Column(name = "quiz_kinds", length = 200)
    private String quizKinds;

    @Column(name = "max_active_bookings_per_user", nullable = false)
    private int maxActiveBookingsPerUser = 3;

    @Column(name = "prevent_duplicate_guide_bookings", nullable = false)
    private boolean preventDuplicateGuideBookings = true;

    @Column(name = "prevent_duplicate_category_bookings", nullable = false)
    private boolean preventDuplicateCategoryBookings = true;

    @Column(name = "require_fairteiler_visit_by_third", nullable = false)
    private boolean requireFairteilerVisitByThird;

    @Column(name = "ignored_categories", length = 400)
    private String ignoredCategories;

    @Column(name = "cleaning_store_id")
    private UUID cleaningStoreId;

    @Column(name = "learning_pages_mandatory", nullable = false)
    private boolean learningPagesMandatory = true;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    public boolean isGroup() {
        return type != null && type.isGroup();
    }

    @Override
    public SubjectType historySubjectType() {
        return isGroup() ? SubjectType.GROUP : SubjectType.REGION;
    }

    @Override
    public UUID historySubjectId() {
        return getId();
    }

    @Override
    public UUID historyRegionId() {
        return isGroup() ? parentId : getId();
    }

    @Override
    public HistoryAction historyFieldChangedAction() {
        return isGroup() ? GroupAction.FIELD_CHANGED : RegionAction.FIELD_CHANGED;
    }
}
