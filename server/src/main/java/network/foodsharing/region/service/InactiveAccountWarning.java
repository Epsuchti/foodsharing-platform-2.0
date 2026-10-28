package network.foodsharing.region.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import network.foodsharing.config.AppProperties;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/**
 * Region maintenance step (A13, upstream inactive-user mail): accounts without login for more than
 * {@code app.region.inactive-days} (0 = disabled) that are not protected by {@code no_automatic_delete} get one
 * warning mail per inactivity period. There is no automatic deletion at launch (decision 70).
 */
@Component
public class InactiveAccountWarning {

    static final String TEMPLATE = "user/inactivity-warning";

    private final UserService users;
    private final MailOutboxService outbox;
    private final AppProperties properties;
    private final Clock clock;

    public InactiveAccountWarning(UserService users, MailOutboxService outbox, AppProperties properties,
                                  Clock clock) {
        this.users = users;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sends the due warnings; returns how many went out. */
    public int warn() {
        int days = properties.getRegion().getInactiveDays();
        if (days <= 0) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(days));
        int sent = 0;
        for (User user : users.inactiveUnwarned(cutoff)) {
            outbox.enqueue(new MailDraft(user.getEmail(), TEMPLATE, user.getPreferredLanguage(),
                    variables(user.getDisplayName(), days), user.getId(), null, null));
            users.markInactivityWarned(user.getId());
            sent++;
        }
        return sent;
    }

    private Map<String, Object> variables(String displayName, int days) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("displayName", displayName);
        variables.put("days", days);
        variables.put("link", properties.getBaseUrl() + "/login");
        return variables;
    }
}
