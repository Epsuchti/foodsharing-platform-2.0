package network.foodsharing.oauthserver.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.oauthserver.domain.OAuthEntities.Code;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthCodeRepository extends JpaRepository<Code, UUID> {

    Optional<Code> findByCodeHash(String codeHash);

    List<Code> findByRequestIdIn(Collection<UUID> requestIds);
}
