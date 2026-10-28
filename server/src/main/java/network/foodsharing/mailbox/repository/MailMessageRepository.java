package network.foodsharing.mailbox.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.mailbox.domain.MailFolder;
import network.foodsharing.mailbox.domain.MailMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailMessageRepository extends JpaRepository<MailMessage, UUID> {

    @Query("select m from MailMessage m where m.mailboxId = :mailboxId and m.folder = :folder and (:pattern is null "
            + "or lower(m.subject) like :pattern or lower(m.fromAddress) like :pattern "
            + "or lower(coalesce(m.fromName, '')) like :pattern or lower(m.toJson) like :pattern "
            + "or lower(coalesce(m.bodyText, '')) like :pattern) order by m.receivedAt desc, m.id")
    Page<MailMessage> search(@Param("mailboxId") UUID mailboxId, @Param("folder") MailFolder folder,
                             @Param("pattern") String pattern, Pageable pageable);

    /** Rows of {@code [mailboxId, count]}: unread INBOX mails per mailbox. */
    @Query("select m.mailboxId, count(m) from MailMessage m where m.mailboxId in :mailboxIds and m.folder = :folder "
            + "and m.readAt is null group by m.mailboxId")
    List<Object[]> unreadCounts(@Param("mailboxIds") Collection<UUID> mailboxIds, @Param("folder") MailFolder folder);

    long countByMailboxId(UUID mailboxId);

    long countBySentByUserIdAndFolderAndReceivedAtAfter(UUID userId, MailFolder folder, Instant after);

    List<MailMessage> findByMessageIdIn(Collection<String> messageIds);

    List<MailMessage> findByMailboxId(UUID mailboxId);

    boolean existsByMailboxIdAndRawHash(UUID mailboxId, String rawHash);

    List<MailMessage> findByFolderAndMessageIdIn(MailFolder folder, Collection<String> messageIds);

    /** Activity feed: INBOX mails of the given mailboxes received before {@code before}, newest first. */
    List<MailMessage> findByMailboxIdInAndFolderAndReceivedAtBeforeOrderByReceivedAtDesc(
            Collection<UUID> mailboxIds, MailFolder folder, Instant before, Pageable pageable);
}
