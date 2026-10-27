package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.LegalDocumentKind;
import network.foodsharing.content.domain.LegalDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocumentVersionRepository extends JpaRepository<LegalDocumentVersion, UUID> {

    List<LegalDocumentVersion> findByKindOrderByPublishedAtDesc(LegalDocumentKind kind);

    Optional<LegalDocumentVersion> findByKindAndDocumentVersion(LegalDocumentKind kind, String version);

    boolean existsByKindAndDocumentVersion(LegalDocumentKind kind, String version);
}
