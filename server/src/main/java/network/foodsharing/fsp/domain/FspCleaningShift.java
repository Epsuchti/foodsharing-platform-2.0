package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/**
 * A one-time cleaning shift or the override of a regular occurrence (FS2); slot count 0 cancels it. Signing up for a
 * regular occurrence creates this row, so later schedule edits never delete taken slots (as pickups, A20).
 */
@Getter
@Setter
@Entity
@Table(name = "fsp_cleaning_shift")
public class FspCleaningShift extends BaseEntity {

    @Column(name = "fsp_id", nullable = false, updatable = false)
    private UUID fspId;

    @Column(name = "shift_at", nullable = false, updatable = false)
    private Instant shiftAt;

    @Column(name = "slot_count", nullable = false)
    private int slotCount;

    @Column(name = "description", length = 100)
    private String description;
}
