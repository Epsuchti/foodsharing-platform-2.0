package network.foodsharing.notification.mail.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import network.foodsharing.api.model.Language;

/**
 * A mail to queue: recipient, template name ({@code templates/mail/<template>_<lang>.html|.txt}, subject from message
 * key {@code mail.<template>.subject}), language (null = de) and template variables.
 */
public record MailDraft(String to, String template, Language language, Map<String, Object> variables, UUID userId,
                        String category, String identifier) {

    /** Category of sign-in mails: highest priority, never rate limited. */
    public static final String AUTH = "AUTH";
    public static final String NEWSLETTER = "NEWSLETTER";

    public MailDraft(String to, String template, Language language, Map<String, Object> variables) {
        this(to, template, language, variables, null, template.startsWith("magic-link") ? AUTH : null, null);
    }


    public MailDraft {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(template, "template");
        // null values are allowed (optional template variables)
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(variables));
    }
}
