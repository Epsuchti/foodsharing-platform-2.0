package network.foodsharing.intropickup.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntroPickupCommentRepository extends JpaRepository<IntroPickupComment, UUID> {

    List<IntroPickupComment> findBySlotIdOrderByCreatedAtAsc(UUID slotId);

    List<IntroPickupComment> findBySlotIdIn(Collection<UUID> slotIds);

    List<IntroPickupComment> findByBookingUserIdOrderByCreatedAtAsc(UUID bookingUserId);
}
