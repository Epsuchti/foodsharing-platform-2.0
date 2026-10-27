package network.foodsharing.auth.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.PasskeyUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasskeyUserEntityRepository extends JpaRepository<PasskeyUserEntity, UUID> {

    Optional<PasskeyUserEntity> findByUserId(UUID userId);

    Optional<PasskeyUserEntity> findByHandle(String handle);
}
