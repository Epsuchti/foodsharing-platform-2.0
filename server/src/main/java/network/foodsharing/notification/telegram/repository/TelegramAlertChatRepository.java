package network.foodsharing.notification.telegram.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.telegram.domain.TelegramAlertChat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramAlertChatRepository extends JpaRepository<TelegramAlertChat, UUID> {

    List<TelegramAlertChat> findByOwnerTypeAndOwnerIdOrderByTitleAscIdAsc(TelegramAlertChat.OwnerType ownerType, UUID ownerId);

    List<TelegramAlertChat> findByOwnerTypeAndOwnerIdAndEnabledTrue(TelegramAlertChat.OwnerType ownerType, UUID ownerId);

    /** Enabled chats per owner. */
    interface OwnerCount {
        UUID getOwnerId();

        long getCount();
    }

    @org.springframework.data.jpa.repository.Query("select c.ownerId as ownerId, count(c) as count from TelegramAlertChat c "
            + "where c.ownerType = :ownerType and c.ownerId in :ownerIds and c.enabled = true group by c.ownerId")
    List<OwnerCount> countEnabled(@org.springframework.data.repository.query.Param("ownerType") TelegramAlertChat.OwnerType ownerType,
                                  @org.springframework.data.repository.query.Param("ownerIds") java.util.Collection<UUID> ownerIds);
}
