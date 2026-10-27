package network.foodsharing.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    List<PasskeyCredential> findByUserIdOrderByCreatedAt(UUID userId);

    Optional<PasskeyCredential> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
