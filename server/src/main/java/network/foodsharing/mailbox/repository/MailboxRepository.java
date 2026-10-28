package network.foodsharing.mailbox.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

    Optional<Mailbox> findByEmailNameIgnoreCase(String emailName);

    boolean existsByEmailNameIgnoreCase(String emailName);

    Optional<Mailbox> findByOwnerTypeAndOwnerId(MailboxOwnerType ownerType, UUID ownerId);

    List<Mailbox> findByOwnerTypeAndOwnerIdIn(MailboxOwnerType ownerType, Collection<UUID> ownerIds);

    List<Mailbox> findByOwnerType(MailboxOwnerType ownerType);

    /** ORGA listing: optional owner type and lower-case {@code %pattern%} over email name and name. */
    @Query("select m from Mailbox m where (:ownerType is null or m.ownerType = :ownerType) and (:pattern is null "
            + "or lower(m.emailName) like :pattern or lower(m.name) like :pattern) order by m.emailName, m.id")
    Page<Mailbox> adminSearch(@Param("ownerType") MailboxOwnerType ownerType, @Param("pattern") String pattern,
                              Pageable pageable);
}
