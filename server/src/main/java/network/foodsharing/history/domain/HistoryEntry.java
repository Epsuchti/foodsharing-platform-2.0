package network.foodsharing.history.domain;

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
import network.foodsharing.history.ActorType;
import network.foodsharing.history.SubjectType;

/** One row of the action history (plan §10.1). Append-only. */
@Getter
@Setter
@Entity
@Table(name = "history_entry")
public class HistoryEntry extends BaseEntity {

    public static final int MAX_VALUE_LENGTH = 32600;
    public static final int MAX_REASON_LENGTH = 2000;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 40)
    private ActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 40)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "secondary_subject_id")
    private UUID secondarySubjectId;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "field", length = 100)
    private String field;

    @Column(name = "old_value", length = MAX_VALUE_LENGTH)
    private String oldValue;

    @Column(name = "new_value", length = MAX_VALUE_LENGTH)
    private String newValue;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "metadata", length = MAX_VALUE_LENGTH)
    private String metadata;

    @Column(name = "region_id")
    private UUID regionId;

    @Column(name = "trace_id", length = 32)
    private String traceId;
}
