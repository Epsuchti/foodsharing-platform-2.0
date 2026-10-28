package network.foodsharing.support.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.support.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID> {

    List<SupportTicket> findByUserId(UUID userId);

    long countByCreatedAtGreaterThanEqual(Instant since);
}
