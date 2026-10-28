package network.foodsharing.automation.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.SignoutAlertSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignoutAlertSettingRepository extends JpaRepository<SignoutAlertSetting, UUID> {

    Optional<SignoutAlertSetting> findByStoreId(UUID storeId);

    List<SignoutAlertSetting> findByStoreIdIn(Collection<UUID> storeIds);

    List<SignoutAlertSetting> findByEnabledTrue();
}
