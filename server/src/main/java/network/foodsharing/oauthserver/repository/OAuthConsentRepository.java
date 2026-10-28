package network.foodsharing.oauthserver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.oauthserver.domain.OAuthEntities.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthConsentRepository extends JpaRepository<Consent, UUID> {

    Optional<Consent> findByClientIdAndUserId(UUID clientId, UUID userId);

    List<Consent> findByUserId(UUID userId);

    List<Consent> findByClientId(UUID clientId);

    long countByClientId(UUID clientId);
}
