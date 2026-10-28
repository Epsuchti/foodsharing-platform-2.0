package network.foodsharing.event.repository;

import java.util.UUID;
import network.foodsharing.event.domain.EventLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLocationRepository extends JpaRepository<EventLocation, UUID> {
}
