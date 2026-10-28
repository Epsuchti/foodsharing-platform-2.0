package network.foodsharing.admin.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.admin.domain.ConfigurationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigurationSettingRepository extends JpaRepository<ConfigurationSetting, UUID> {

    Optional<ConfigurationSetting> findByConfigKey(String configKey);
}
