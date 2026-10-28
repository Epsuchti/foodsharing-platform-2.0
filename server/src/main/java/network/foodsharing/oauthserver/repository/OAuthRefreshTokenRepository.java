package network.foodsharing.oauthserver.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.oauthserver.domain.OAuthEntities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthRefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndClientIdAndRevokedAtIsNull(UUID userId, UUID clientId);

    List<RefreshToken> findByUserId(UUID userId);

    List<RefreshToken> findByClientIdAndRevokedAtIsNull(UUID clientId);

    List<RefreshToken> findByExpiresAtBefore(Instant before);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID userId, Instant now);
}
