package network.foodsharing.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.EmailChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChangeRepository extends JpaRepository<EmailChange, UUID> {

    List<EmailChange> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<EmailChange> findByTokenHash(String tokenHash);

    Optional<EmailChange> findByAbortTokenHash(String abortTokenHash);

    void deleteByUserId(UUID userId);
}
