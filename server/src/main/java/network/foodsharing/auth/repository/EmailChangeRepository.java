package network.foodsharing.auth.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.EmailChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailChangeRepository extends JpaRepository<EmailChange, UUID> {

    List<EmailChange> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    Optional<EmailChange> findByTokenHash(String tokenHash);

    Optional<EmailChange> findByAbortTokenHash(String abortTokenHash);

    void deleteByUserId(UUID userId);

    /** Cleanup: requests (pending, completed or aborted) that expired before {@code before}. */
    @Modifying
    @Query("delete from EmailChange c where c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
