package network.foodsharing.intropickup.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.intropickup.domain.IntroPickupCategory;
import network.foodsharing.intropickup.domain.PrivateInfoTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivateInfoTemplateRepository extends JpaRepository<PrivateInfoTemplate, UUID> {

    List<PrivateInfoTemplate> findByGuideIdOrderByCategoryAsc(UUID guideId);

    Optional<PrivateInfoTemplate> findByGuideIdAndCategory(UUID guideId, IntroPickupCategory category);
}
