package network.foodsharing.mailbox.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of mailbox memberships (A40); the personal mailbox is deleted with its mails (D17.3).
 */
@Component
public class MailboxAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;
    private final MailboxService mailboxes;

    public MailboxAccountData(EntityManager em, MailboxService mailboxes) {
        this.em = em;
        this.mailboxes = mailboxes;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "MailboxMember", "userId", userId);
        mailboxes.deletePersonalMailbox(userId);
    }

    @Override
    public String section() {
        return "mailboxes";
    }

    @Override
    public Object export(UUID userId) {
        return UserRows.read(em, "MailboxMember", "userId", userId, "mailboxId", "createdAt");
    }
}
