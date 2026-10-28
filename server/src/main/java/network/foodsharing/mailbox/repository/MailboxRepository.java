package network.foodsharing.mailbox.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

    Optional<Mailbox> findByEmailNameIgnoreCase(String emailName);

    boolean existsByEmailNameIgnoreCase(String emailName);

    Optional<Mailbox> findByOwnerTypeAndOwnerId(MailboxOwnerType ownerType, UUID ownerId);

    List<Mailbox> findByOwnerTypeAndOwnerIdIn(MailboxOwnerType ownerType, Collection<UUID> ownerIds);

    List<Mailbox> findByOwnerType(MailboxOwnerType ownerType);
}
