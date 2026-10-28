package network.foodsharing.admin.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.admin.domain.FeatureToggleSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureToggleSettingRepository extends JpaRepository<FeatureToggleSetting, UUID> {

    Optional<FeatureToggleSetting> findByToggleKey(String toggleKey);
}
