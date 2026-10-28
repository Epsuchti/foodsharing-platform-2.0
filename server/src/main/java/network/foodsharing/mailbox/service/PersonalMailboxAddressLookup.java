package network.foodsharing.mailbox.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.config.AppProperties;
import network.foodsharing.mailbox.repository.MailboxRepository;
import network.foodsharing.user.service.BusinessCardPort;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code <email_name>@<app.mailbox.domain>} of the user's personal mailbox (business card, A14). */
@Component
public class PersonalMailboxAddressLookup implements BusinessCardPort.PersonalMailboxAddress {

    private final UserService users;
    private final MailboxRepository mailboxes;
    private final AppProperties properties;

    public PersonalMailboxAddressLookup(UserService users, MailboxRepository mailboxes, AppProperties properties) {
        this.users = users;
        this.mailboxes = mailboxes;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> of(UUID userId) {
        String domain = properties.getMailbox().getDomain();
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return users.findActive(userId).map(user -> user.getMailboxId()).flatMap(mailboxes::findById)
                .map(mailbox -> mailbox.getEmailName() + "@" + domain);
    }
}
