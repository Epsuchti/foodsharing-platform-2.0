package network.foodsharing.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationCategory;
import network.foodsharing.notification.domain.UserNotificationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSetting, UUID> {

    List<UserNotificationSetting> findByUserId(UUID userId);

    Optional<UserNotificationSetting> findByUserIdAndCategory(UUID userId, NotificationCategory category);
}
