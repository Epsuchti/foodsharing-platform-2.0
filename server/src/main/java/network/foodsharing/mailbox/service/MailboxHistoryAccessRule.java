package network.foodsharing.mailbox.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** Mailbox history: its readers and managers (ORGA always through the history policy). */
@Component
public class MailboxHistoryAccessRule implements HistoryAccessRule {

    private final MailboxRepository mailboxes;
    private final MailboxAccessService access;

    public MailboxHistoryAccessRule(MailboxRepository mailboxes, MailboxAccessService access) {
        this.mailboxes = mailboxes;
        this.access = access;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.MAILBOX;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return mailboxes.findById(subjectId).map(m -> access.mayRead(actor, m) || access.mayManage(actor, m))
                .orElse(false);
    }
}
