package network.foodsharing.notification.newsletter.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import network.foodsharing.notification.domain.NewsletterStatus;
import network.foodsharing.notification.newsletter.domain.Newsletter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsletterRepository extends JpaRepository<Newsletter, UUID> {

    /** {@code allRegions}: every newsletter incl. global ones; otherwise only those of {@code regionIds}. */
    @Query("select n from Newsletter n where (:allRegions = true or n.regionId in :regionIds) "
            + "and (:anyStatus = true or n.status = :status) order by n.createdAt desc, n.id")
    Page<Newsletter> search(@Param("allRegions") boolean allRegions, @Param("regionIds") Collection<UUID> regionIds,
                            @Param("anyStatus") boolean anyStatus, @Param("status") NewsletterStatus status,
                            Pageable pageable);

    List<Newsletter> findByStatusIn(Collection<NewsletterStatus> statuses);
}
