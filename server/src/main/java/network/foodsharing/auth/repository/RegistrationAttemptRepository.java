package network.foodsharing.auth.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.auth.domain.RegistrationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationAttemptRepository extends JpaRepository<RegistrationAttempt, UUID> {

    /** Cleanup: attempts recorded before {@code before}. */
    @Modifying
    @Query("delete from RegistrationAttempt a where a.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
