package network.foodsharing.notification.newsletter.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.newsletter.domain.NewsletterRecipient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsletterRecipientRepository extends JpaRepository<NewsletterRecipient, UUID> {

    List<NewsletterRecipient> findByNewsletterIdAndStatusOrderByCreatedAtAscIdAsc(
            UUID newsletterId, NewsletterRecipient.Status status, Pageable pageable);

    long countByNewsletterIdAndStatus(UUID newsletterId, NewsletterRecipient.Status status);

    @Modifying
    @Query("delete from NewsletterRecipient r where r.newsletterId = :newsletterId and r.status = :status")
    int deleteByNewsletterIdAndStatus(@Param("newsletterId") UUID newsletterId,
                                      @Param("status") NewsletterRecipient.Status status);
}
