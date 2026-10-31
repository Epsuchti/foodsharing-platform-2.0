package network.foodsharing.oauthserver.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.oauthserver.domain.OAuthEntities.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByClientIdAndDeletedAtIsNull(String clientId);

    boolean existsByClientId(String clientId);

    List<Client> findByDeletedAtIsNullOrderByNameAscIdAsc();
}
