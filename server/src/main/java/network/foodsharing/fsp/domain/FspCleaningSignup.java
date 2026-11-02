package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A user signed up for the cleaning shift {@code (fspId, shiftAt)} (FS2). */
@Getter
@Setter
@Entity
@Table(name = "fsp_cleaning_signup")
public class FspCleaningSignup extends BaseEntity {

    @Column(name = "fsp_id", nullable = false, updatable = false)
    private UUID fspId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "shift_at", nullable = false, updatable = false)
    private Instant shiftAt;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;
}
