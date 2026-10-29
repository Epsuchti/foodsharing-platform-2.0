package network.foodsharing.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @Query("select s from AuthSession s join fetch s.user where s.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Query("select s from AuthSession s where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now "
            + "order by s.lastSeenAt desc")
    List<AuthSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("select s from AuthSession s where s.id = :id and s.user.id = :userId")
    Optional<AuthSession> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Bulk update without version check: concurrent requests of one session must not conflict. */
    @Modifying
    @Query("update AuthSession s set s.lastSeenAt = :now, s.expiresAt = :expiresAt where s.id = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now, @Param("expiresAt") Instant expiresAt);

    /** Cleanup: sessions expired or revoked before {@code before}. */
    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :before or s.revokedAt < :before")
    int deleteEndedBefore(@Param("before") Instant before);
}
