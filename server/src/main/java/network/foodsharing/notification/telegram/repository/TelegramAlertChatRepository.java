package network.foodsharing.notification.telegram.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramAlertChatRepository extends JpaRepository<TelegramAlertChat, UUID> {

    List<TelegramAlertChat> findByOwnerTypeAndOwnerIdOrderByTitleAscIdAsc(TelegramAlertChat.OwnerType ownerType, UUID ownerId);

    List<TelegramAlertChat> findByOwnerTypeAndOwnerIdAndEnabledTrue(TelegramAlertChat.OwnerType ownerType, UUID ownerId);
}
