package network.foodsharing.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.LoginToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginTokenRepository extends JpaRepository<LoginToken, UUID> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    long countByEmailAndCreatedAtAfter(String email, Instant after);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from LoginToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@org.springframework.data.repository.query.Param("before") Instant before);
}
