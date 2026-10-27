package network.foodsharing.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.auth.domain.AuthIdentity;
import network.foodsharing.auth.domain.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    @Query("select i from AuthIdentity i join fetch i.user where i.provider = :provider and i.subject = :subject")
    Optional<AuthIdentity> findByProviderAndSubject(@Param("provider") IdentityProvider provider,
                                                    @Param("subject") String subject);

    @Query("select i from AuthIdentity i where i.user.id = :userId order by i.createdAt")
    List<AuthIdentity> findByUserId(@Param("userId") UUID userId);

    @Query("select i from AuthIdentity i where i.id = :id and i.user.id = :userId")
    Optional<AuthIdentity> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select count(i) from AuthIdentity i where i.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
