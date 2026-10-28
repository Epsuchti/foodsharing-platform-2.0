package network.foodsharing.profile.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.profile.domain.Buddy;
import network.foodsharing.user.domain.BuddyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuddyRepository extends JpaRepository<Buddy, UUID> {

    Optional<Buddy> findByUserIdAndBuddyId(UUID userId, UUID buddyId);

    List<Buddy> findByUserIdAndStatus(UUID userId, BuddyStatus status);

    List<Buddy> findByBuddyIdAndStatus(UUID buddyId, BuddyStatus status);

    long countByUserIdAndStatus(UUID userId, BuddyStatus status);

    void deleteByUserIdOrBuddyId(UUID userId, UUID buddyId);
}
