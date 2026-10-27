package network.foodsharing.notification.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.domain.Bell;
import network.foodsharing.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BellRepository extends JpaRepository<Bell, UUID> {

    List<Bell> findByIdentifierAndDeletedAtIsNull(String identifier);

    boolean existsByIdentifierAndCreatedAtAfter(String identifier, java.time.Instant since);

    @Query("select b from Bell b where (:userId is null or b.userId = :userId) and (:type is null or b.type = :type) "
            + "order by b.createdAt desc, b.id")
    List<Bell> search(@Param("userId") UUID userId, @Param("type") NotificationType type);
}
