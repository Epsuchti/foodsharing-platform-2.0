package network.foodsharing.notification.telegram.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.notification.telegram.domain.TelegramLinkToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, UUID> {

    Optional<TelegramLinkToken> findByToken(String token);

    @Modifying
    @Query("delete from TelegramLinkToken t where t.userId = :userId and t.usedAt is null")
    int deleteOpenByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from TelegramLinkToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
