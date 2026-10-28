package network.foodsharing.mailbox.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.mailbox.domain.MailFolder;
import network.foodsharing.mailbox.domain.Mailbox;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** New mails of the user's readable mailboxes for the dashboard activity feed (A19; subject and sender only). */
@Service
@Transactional(readOnly = true)
public class MailboxFeedService {

    /** One received mail; the body is never exposed to the feed. */
    public record FeedMail(UUID id, UUID mailboxId, String mailboxName, String subject, String fromName,
                           String fromAddress, Instant receivedAt) {
    }

    private final MailboxAccessService access;
    private final MailMessageRepository mails;

    public MailboxFeedService(MailboxAccessService access, MailMessageRepository mails) {
        this.access = access;
        this.mails = mails;
    }

    public List<FeedMail> latest(UUID userId, Instant before, int limit) {
        Map<UUID, Mailbox> boxes = access.visibleMailboxes(new SessionPrincipal(userId, null, null, null)).stream()
                .collect(Collectors.toMap(Mailbox::getId, Function.identity()));
        if (boxes.isEmpty()) {
            return List.of();
        }
        return mails.findByMailboxIdInAndFolderAndReceivedAtBeforeOrderByReceivedAtDesc(boxes.keySet(),
                        MailFolder.INBOX, before, PageRequest.of(0, Math.max(limit, 1))).stream()
                .map(m -> new FeedMail(m.getId(), m.getMailboxId(), boxes.get(m.getMailboxId()).getName(),
                        m.getSubject(), m.getFromName(), m.getFromAddress(), m.getReceivedAt())).toList();
    }
}
