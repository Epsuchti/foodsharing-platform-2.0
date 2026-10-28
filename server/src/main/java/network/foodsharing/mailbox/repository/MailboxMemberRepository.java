package network.foodsharing.mailbox.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.mailbox.domain.MailboxMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailboxMemberRepository extends JpaRepository<MailboxMember, UUID> {

    List<MailboxMember> findByMailboxId(UUID mailboxId);

    boolean existsByMailboxIdAndUserId(UUID mailboxId, UUID userId);

    @Query("select m.mailboxId from MailboxMember m where m.userId = :userId")
    List<UUID> mailboxIdsOf(@Param("userId") UUID userId);
}
