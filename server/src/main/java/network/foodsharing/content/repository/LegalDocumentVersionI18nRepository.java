package network.foodsharing.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.LegalDocumentVersionI18n;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDocumentVersionI18nRepository extends JpaRepository<LegalDocumentVersionI18n, UUID> {

    List<LegalDocumentVersionI18n> findByOwnerId(UUID ownerId);
}
