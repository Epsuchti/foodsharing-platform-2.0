package network.foodsharing.intropickup.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.GuideProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideProfileRepository extends JpaRepository<GuideProfile, UUID> {

    Optional<GuideProfile> findByUserId(UUID userId);

    Optional<GuideProfile> findByIcalFeedToken(String token);

    boolean existsByIcalFeedToken(String token);
}
