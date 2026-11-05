package network.foodsharing.pickup.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.pickup.domain.PickupDayConversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupDayConversationRepository extends JpaRepository<PickupDayConversation, UUID> {

    Optional<PickupDayConversation> findByStoreIdAndPickupDate(UUID storeId, LocalDate pickupDate);
}
