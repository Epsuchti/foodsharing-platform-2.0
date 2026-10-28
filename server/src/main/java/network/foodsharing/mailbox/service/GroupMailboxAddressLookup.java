package network.foodsharing.mailbox.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.domain.MailboxOwnerType;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.region.service.GroupMailboxAddress;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code <email_name>@<app.mailbox.domain>} of a region's / working group's mailbox (mediation team, G2). */
@Component
public class GroupMailboxAddressLookup implements GroupMailboxAddress {

    private final MailboxRepository mailboxes;
    private final AppProperties properties;

    public GroupMailboxAddressLookup(MailboxRepository mailboxes, AppProperties properties) {
        this.mailboxes = mailboxes;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> of(UUID regionOrGroupId) {
        String domain = properties.getMailbox().getDomain();
        if (domain == null || domain.isBlank() || regionOrGroupId == null) {
            return Optional.empty();
        }
        return mailboxes.findByOwnerTypeAndOwnerId(MailboxOwnerType.REGION, regionOrGroupId)
                .map(mailbox -> mailbox.getEmailName() + "@" + domain);
    }
}
