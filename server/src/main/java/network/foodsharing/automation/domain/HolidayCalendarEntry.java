package network.foodsharing.automation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** A public holiday of the static provider; {@code canton == null} = national holiday. */
@Getter
@Setter
@Entity
@Table(name = "holiday_calendar")
public class HolidayCalendarEntry extends BaseEntity {

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "canton", length = 2)
    private String canton;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "name", nullable = false, length = 200)
    private String name;
}
