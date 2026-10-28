package network.foodsharing.calendar.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.calendar.domain.CalendarToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarTokenRepository extends JpaRepository<CalendarToken, UUID> {

    Optional<CalendarToken> findByUserId(UUID userId);

    Optional<CalendarToken> findByToken(String token);
}
