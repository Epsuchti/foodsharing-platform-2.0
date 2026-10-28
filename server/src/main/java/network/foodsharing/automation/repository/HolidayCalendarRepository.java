package network.foodsharing.automation.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import network.foodsharing.automation.domain.HolidayCalendarEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendarEntry, UUID> {

    @Query("select h from HolidayCalendarEntry h where h.country = :country and h.holidayDate >= :from "
            + "and h.holidayDate < :to and (h.canton is null or h.canton = :canton) order by h.holidayDate, h.name")
    List<HolidayCalendarEntry> findInRange(@Param("country") String country, @Param("canton") String canton,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to);
}
