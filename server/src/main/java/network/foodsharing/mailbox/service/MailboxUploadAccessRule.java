package network.foodsharing.mailbox.service;

import java.util.UUID;
import network.foodsharing.mailbox.domain.MailMessage;
import network.foodsharing.mailbox.repository.MailMessageRepository;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.upload.service.UploadAccessRule;
import org.springframework.stereotype.Component;

/** A34/A35: an attachment upload (usage owner = mail id) is readable by the readers of the mail's mailbox. */
@Component
public class MailboxUploadAccessRule implements UploadAccessRule {

    private final MailMessageRepository mails;
    private final MailboxRepository mailboxes;
    private final MailboxAccessService access;

    public MailboxUploadAccessRule(MailMessageRepository mails, MailboxRepository mailboxes,
                                   MailboxAccessService access) {
        this.mails = mails;
        this.mailboxes = mailboxes;
        this.access = access;
    }

    @Override
    public String module() {
        return MailAttachmentStore.MODULE;
    }

    @Override
    public boolean mayRead(UUID userId, UUID usageOwnerId) {
        if (userId == null || usageOwnerId == null) {
            return false;
        }
        return mails.findById(usageOwnerId).map(MailMessage::getMailboxId).flatMap(mailboxes::findById)
                .map(mailbox -> access.readers(mailbox).contains(userId)).orElse(false);
    }

    /** An attachment of a deleted mail can go. */
    @Override
    public boolean exists(UUID usageOwnerId) {
        return mails.existsById(usageOwnerId);
    }
}
