package network.foodsharing.oauthserver.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.oauthserver.domain.OAuthEntities.AuthorizationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthRequestRepository extends JpaRepository<AuthorizationRequest, UUID> {

    List<AuthorizationRequest> findByExpiresAtBefore(Instant before);

    List<AuthorizationRequest> findByUserId(UUID userId);

    List<AuthorizationRequest> findByClientId(UUID clientId);
}
