package network.foodsharing.notification.mail.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, UUID> {

    @Query("select m.id from MailOutboxMessage m where m.status = :status and m.nextAttemptAt <= :now "
            + "order by m.priority desc, m.nextAttemptAt, m.createdAt")
    List<UUID> findDueIds(@Param("status") MailStatus status, @Param("now") Instant now, Pageable page);

    /** Slot times (deferral or creation) of non-AUTH mails to a recipient since {@code since}, oldest first (A27). */
    @Query("select coalesce(m.rateLimitedUntil, m.createdAt) from MailOutboxMessage m where m.toAddress = :to "
            + "and m.priority = 0 and coalesce(m.rateLimitedUntil, m.createdAt) > :since "
            + "order by coalesce(m.rateLimitedUntil, m.createdAt)")
    List<Instant> slotsSince(@Param("to") String to, @Param("since") Instant since);

    @Query("select coalesce(m.rateLimitedUntil, m.createdAt) from MailOutboxMessage m where m.toAddress = :to "
            + "and m.template = :template and m.priority = 0 and coalesce(m.rateLimitedUntil, m.createdAt) > :since "
            + "order by coalesce(m.rateLimitedUntil, m.createdAt)")
    List<Instant> templateSlotsSince(@Param("to") String to, @Param("template") String template,
                                     @Param("since") Instant since);

    boolean existsByToAddressAndTemplateAndIdentifierAndCreatedAtAfter(String to, String template, String identifier,
                                                                      Instant since);

    @Query("select m from MailOutboxMessage m where (:status is null or m.status = :status) and (:search is null "
            + "or lower(m.toAddress) like :search or lower(m.subject) like :search or lower(m.template) like :search)")
    Page<MailOutboxMessage> search(@Param("status") MailStatus status, @Param("search") String search,
                                   Pageable page);

    @Modifying
    @Query("delete from MailOutboxMessage m where m.status = :status and m.sentAt < :before")
    int deleteByStatusAndSentAtBefore(@Param("status") MailStatus status, @Param("before") Instant before);
}
