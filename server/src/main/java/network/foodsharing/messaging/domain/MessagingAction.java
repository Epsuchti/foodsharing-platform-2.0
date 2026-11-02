package network.foodsharing.messaging.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/**
 * Conversation history (subject CONVERSATION; docs/conventions.md A33). Message bodies are not recorded, except the
 * original text of an edited or deleted message ({@link #MESSAGE_EDITED}, {@link #MESSAGE_DELETED}; secondary subject
 * = the message), which only the chat's moderators may read ({@code MessagingHistoryAccessRule#mayReadValues}).
 */
public enum MessagingAction implements HistoryAction {
    CONVERSATION_CREATED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    RENAMED,
    SYSTEM_MESSAGE_POSTED,
    MEMBERS_SYNCED,
    /** oldValue = text before the edit, newValue = text after it. */
    MESSAGE_EDITED,
    /** oldValue = text before the delete; reason = the moderator's reason (moderator deletes only). */
    MESSAGE_DELETED;

    @Override
    public String code() {
        return name();
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.CONVERSATION;
    }

    @Override
    public RetentionPolicy retention() {
        return RetentionPolicy.YEARS_2;
    }
}
