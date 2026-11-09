package network.foodsharing.notification.mail.service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import network.foodsharing.common.seed.SeedPersonas;
import network.foodsharing.config.AppProperties;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Who may get a mail ({@code app.mail.recipient-policy}, decision 173). {@code all} (default, prod): everybody.
 * {@code staging} (the public test environment): only
 * <ul>
 *   <li>addresses in {@code app.mail.allowed-recipients},</li>
 *   <li>auth mails (category {@link MailDraft#AUTH}: magic link login and sign-up, email change), so real testers
 *       can register,</li>
 *   <li>the address of an existing, active user that is not a seed user.</li>
 * </ul>
 * Addresses on reserved, never deliverable domains ({@code @local}, {@code *.test}, {@code *.invalid}, …: the seed
 * data uses them) are always suppressed unless allowed explicitly.
 */
@Component
public class MailRecipientPolicy {

    public static final String ALL = "all";
    public static final String STAGING = "staging";
    /** {@code last_error} of a suppressed outbox mail (status SENT, so it is never retried). */
    public static final String SUPPRESSED = "suppressed (staging recipient policy)";

    private static final Set<String> RESERVED_DOMAINS = Set.of("local", "localhost", "test", "invalid", "example");

    private final AppProperties properties;
    private final ObjectProvider<UserService> users;

    public MailRecipientPolicy(AppProperties properties, ObjectProvider<UserService> users) {
        this.properties = properties;
        this.users = users;
    }

    public boolean restricted() {
        return STAGING.equalsIgnoreCase(properties.getMail().getRecipientPolicy());
    }

    /**
     * @param to       recipient address
     * @param category outbox category ({@link MailDraft#AUTH} for sign-in mails), may be null
     */
    public boolean allows(String to, String category) {
        if (!restricted()) {
            return true;
        }
        if (to == null || to.isBlank()) {
            return false;
        }
        String address = UserService.normalizeEmail(to);
        if (allowedRecipients().contains(address)) {
            return true;
        }
        if (reservedDomain(address)) {
            return false;
        }
        if (MailDraft.AUTH.equals(category)) {
            return true;
        }
        UserService service = users.getIfAvailable();
        return service != null && service.findByEmail(address)
                .filter(User::isActive)
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !SeedPersonas.isSeedUserId(user.getId()))
                .isPresent();
    }

    private Set<String> allowedRecipients() {
        return properties.getMail().getAllowedRecipients().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(UserService::normalizeEmail)
                .collect(Collectors.toSet());
    }

    static boolean reservedDomain(String address) {
        int at = address.lastIndexOf('@');
        String domain = (at < 0 ? "" : address.substring(at + 1)).toLowerCase(Locale.ROOT);
        if (domain.isEmpty()) {
            return true;
        }
        int dot = domain.lastIndexOf('.');
        String tld = dot < 0 ? domain : domain.substring(dot + 1);
        return RESERVED_DOMAINS.contains(tld);
    }
}
