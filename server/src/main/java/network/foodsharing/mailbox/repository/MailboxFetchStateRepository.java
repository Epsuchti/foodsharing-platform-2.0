package network.foodsharing.mailbox.repository;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.mailbox.domain.MailboxFetchState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxFetchStateRepository extends JpaRepository<MailboxFetchState, UUID> {

    Optional<MailboxFetchState> findBySource(String source);
}
