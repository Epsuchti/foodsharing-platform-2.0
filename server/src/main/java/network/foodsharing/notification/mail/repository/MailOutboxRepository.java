package network.foodsharing.notification.mail.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.mail.domain.MailOutboxMessage;
import network.foodsharing.notification.mail.domain.MailStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MailOutboxRepository extends JpaRepository<MailOutboxMessage, UUID> {

    @Query("select m.id from MailOutboxMessage m where m.status = :status and m.nextAttemptAt <= :now "
            + "order by m.nextAttemptAt, m.createdAt")
    List<UUID> findDueIds(@Param("status") MailStatus status, @Param("now") Instant now, Pageable page);
}
