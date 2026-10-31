package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.LegalAcceptance;
import network.foodsharing.content.domain.LegalDocumentKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalAcceptanceRepository extends JpaRepository<LegalAcceptance, UUID> {

    List<LegalAcceptance> findByUserIdOrderByAcceptedAtDescIdDesc(UUID userId);

    Optional<LegalAcceptance> findByUserIdAndKindAndDocumentVersion(UUID userId, LegalDocumentKind kind, String version);
}
