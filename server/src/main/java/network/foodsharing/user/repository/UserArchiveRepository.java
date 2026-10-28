package network.foodsharing.user.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.user.domain.UserArchive;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserArchiveRepository extends JpaRepository<UserArchive, UUID> {

    Optional<UserArchive> findByUserId(UUID userId);
}
