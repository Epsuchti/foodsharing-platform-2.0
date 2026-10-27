package network.foodsharing.notification.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.notification.domain.NotificationType;

/**
 * One notification to one or more users (docs/conventions.md A12). Every recipient gets a bell; unless
 * {@code bellOnly}, recipients also get the mail {@code mailTemplate} ({@code templates/mail/<module>/<name>}) rendered
 * with {@code params} plus {@code link}. {@code identifier} lets a later action remove the bells again.
 */
public record NotificationRequest(Set<UUID> recipientIds, NotificationType type, Map<String, String> params,
                                  String link, String mailTemplate, boolean bellOnly, String identifier, String icon) {

    public NotificationRequest {
        Objects.requireNonNull(type, "type");
        recipientIds = recipientIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(recipientIds));
        params = params == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(params));
    }

    public static Builder to(Collection<UUID> recipientIds, NotificationType type) {
        return new Builder(recipientIds, type);
    }

    public static final class Builder {
        private final Set<UUID> recipientIds;
        private final NotificationType type;
        private final Map<String, String> params = new LinkedHashMap<>();
        private String link;
        private String mailTemplate;
        private boolean bellOnly = true;
        private String identifier;
        private String icon;

        private Builder(Collection<UUID> recipientIds, NotificationType type) {
            this.recipientIds = new LinkedHashSet<>(recipientIds);
            this.type = type;
        }

        public Builder param(String key, String value) {
            if (value != null) {
                params.put(key, value);
            }
            return this;
        }

        public Builder link(String link) {
            this.link = link;
            return this;
        }

        /** Also send this mail template (sets bellOnly=false). */
        public Builder mail(String template) {
            this.mailTemplate = template;
            this.bellOnly = template == null;
            return this;
        }

        public Builder identifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public NotificationRequest build() {
            return new NotificationRequest(recipientIds, type, params, link, mailTemplate, bellOnly, identifier, icon);
        }
    }
}
