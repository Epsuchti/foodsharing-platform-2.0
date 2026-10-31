package network.foodsharing.profile.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.profile.domain.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    Optional<Rating> findByRecipientIdAndSenderId(UUID recipientId, UUID senderId);

    List<Rating> findByRecipientIdOrderByCreatedAtDescIdDesc(UUID recipientId);

    List<Rating> findBySenderIdOrderByCreatedAtDescIdDesc(UUID senderId);

    long countByRecipientId(UUID recipientId);

    void deleteByRecipientIdOrSenderId(UUID recipientId, UUID senderId);
}
