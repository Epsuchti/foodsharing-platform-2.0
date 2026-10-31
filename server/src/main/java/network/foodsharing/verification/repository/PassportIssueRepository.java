package network.foodsharing.verification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.verification.domain.PassportIssue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassportIssueRepository extends JpaRepository<PassportIssue, UUID> {

    Optional<PassportIssue> findFirstByUserIdOrderByValidUntilDescCreatedAtDescIdDesc(UUID userId);

    List<PassportIssue> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);
}
