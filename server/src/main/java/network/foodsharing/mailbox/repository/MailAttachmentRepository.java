package network.foodsharing.mailbox.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.mailbox.domain.MailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailAttachmentRepository extends JpaRepository<MailAttachment, UUID> {

    List<MailAttachment> findByMailIdOrderByCreatedAtAscIdAsc(UUID mailId);

    List<MailAttachment> findByMailIdIn(Collection<UUID> mailIds);

    Optional<MailAttachment> findByIdAndMailId(UUID id, UUID mailId);

    long countByUploadId(UUID uploadId);

    Optional<MailAttachment> findFirstByUploadId(UUID uploadId);
}
