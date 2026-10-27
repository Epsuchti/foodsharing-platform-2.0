package network.foodsharing.auth.repository;

import java.util.UUID;
import network.foodsharing.auth.domain.RegistrationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationAttemptRepository extends JpaRepository<RegistrationAttempt, UUID> {
}
