package network.foodsharing.user.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.UserArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserArchiveRepository extends JpaRepository<UserArchive, UUID> {

    Optional<UserArchive> findByUserId(UUID userId);

    /** Cleanup: archives of accounts deleted before {@code before}. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from UserArchive a where a.createdAt < :before")
    int deleteCreatedBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before);
}
