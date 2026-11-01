package network.foodsharing.messaging.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import network.foodsharing.messaging.domain.ConversationMessage;
import network.foodsharing.messaging.domain.MessageEditRules;

/**
 * The viewer's {@code mayEdit} / {@code mayDelete} for the messages of one conversation, so the client never copies
 * {@link MessageEditRules}.
 */
public record MessagePermissions(UUID viewerId, boolean moderator, Instant now, Duration window) {

    public boolean mayEdit(ConversationMessage message) {
        return MessageEditRules.mayEdit(viewerId, message, now, window);
    }

    public boolean mayDelete(ConversationMessage message) {
        return MessageEditRules.mayDelete(viewerId, message, now, window, moderator);
    }
}
