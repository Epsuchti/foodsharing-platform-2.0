package network.foodsharing.fsp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** One weekly cleaning shift of a Fairteiler (FS2); local time in the Fairteiler region's zone. */
@Getter
@Setter
@Entity
@Table(name = "fsp_cleaning_regular")
public class FspCleaningRegular extends BaseEntity {

    @Column(name = "fsp_id", nullable = false, updatable = false)
    private UUID fspId;

    @Enumerated(EnumType.STRING)
    @Column(name = "weekday", nullable = false, length = 10)
    private DayOfWeek weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "slot_count", nullable = false)
    private int slotCount;

    @Column(name = "description", length = 100)
    private String description;
}
