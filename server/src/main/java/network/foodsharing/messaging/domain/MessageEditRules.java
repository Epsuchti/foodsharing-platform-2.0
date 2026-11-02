package network.foodsharing.messaging.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Who may edit or delete a chat message (A33, resume item 16). Pure rules, the caller supplies the time, the edit
 * window ({@code messaging.edit-window-hours}) and whether the actor moderates the conversation.
 * <ul>
 *   <li>System messages (no sender) and deleted messages can never be edited or deleted.</li>
 *   <li>The sender may edit and delete within the window ({@code sentAt + window}, inclusive).</li>
 *   <li>A moderator may delete any message at any time, with a reason, unless the sender rule already allows it.
 *       Moderators never edit other people's messages.</li>
 * </ul>
 */
public final class MessageEditRules {

    /** Outcome of a check, in the order the checks run. */
    public enum Verdict {
        /** Allowed as the sender (no reason needed). */
        ALLOWED,
        /** Delete allowed only as a moderator: a reason is required. */
        ALLOWED_AS_MODERATOR,
        /** The message is already deleted. */
        DELETED,
        /** A system message, someone else's message, or an edit by a moderator. */
        NOT_EDITABLE,
        /** The sender's window has passed. */
        WINDOW_EXPIRED
    }

    private MessageEditRules() {
    }

    public static Verdict edit(UUID actorId, ConversationMessage message, Instant now, Duration window) {
        if (message.isDeleted()) {
            return Verdict.DELETED;
        }
        if (message.getSenderId() == null || !message.getSenderId().equals(actorId)) {
            return Verdict.NOT_EDITABLE;
        }
        return withinWindow(message, now, window) ? Verdict.ALLOWED : Verdict.WINDOW_EXPIRED;
    }

    public static Verdict delete(UUID actorId, ConversationMessage message, Instant now, Duration window,
                                 boolean isModerator) {
        if (message.isDeleted()) {
            return Verdict.DELETED;
        }
        if (message.getSenderId() == null) {
            return Verdict.NOT_EDITABLE;
        }
        boolean own = message.getSenderId().equals(actorId);
        if (own && withinWindow(message, now, window)) {
            return Verdict.ALLOWED;
        }
        if (isModerator) {
            return Verdict.ALLOWED_AS_MODERATOR;
        }
        return own ? Verdict.WINDOW_EXPIRED : Verdict.NOT_EDITABLE;
    }

    public static boolean mayEdit(UUID actorId, ConversationMessage message, Instant now, Duration window) {
        return edit(actorId, message, now, window) == Verdict.ALLOWED;
    }

    public static boolean mayDelete(UUID actorId, ConversationMessage message, Instant now, Duration window,
                                    boolean isModerator) {
        Verdict verdict = delete(actorId, message, now, window, isModerator);
        return verdict == Verdict.ALLOWED || verdict == Verdict.ALLOWED_AS_MODERATOR;
    }

    /** True when the delete is allowed only as a moderator (then a reason is required). */
    public static boolean requiresReason(UUID actorId, ConversationMessage message, Instant now, Duration window,
                                         boolean isModerator) {
        return delete(actorId, message, now, window, isModerator) == Verdict.ALLOWED_AS_MODERATOR;
    }

    static boolean withinWindow(ConversationMessage message, Instant now, Duration window) {
        return !now.isAfter(message.getSentAt().plus(window));
    }
}
