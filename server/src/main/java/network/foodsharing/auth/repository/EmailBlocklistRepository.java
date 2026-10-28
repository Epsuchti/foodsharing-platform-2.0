package network.foodsharing.auth.repository;

import java.util.UUID;
import network.foodsharing.auth.domain.EmailBlocklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailBlocklistRepository extends JpaRepository<EmailBlocklistEntry, UUID> {

    java.util.Optional<EmailBlocklistEntry> findByPatternIgnoreCase(String pattern);
}
