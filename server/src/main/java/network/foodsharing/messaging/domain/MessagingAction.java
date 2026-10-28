package network.foodsharing.messaging.domain;

import network.foodsharing.history.HistoryAction;
import network.foodsharing.history.RetentionPolicy;
import network.foodsharing.history.SubjectType;

/** Conversation history (subject CONVERSATION; docs/conventions.md A33). Message bodies are never recorded. */
public enum MessagingAction implements HistoryAction {
    CONVERSATION_CREATED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    RENAMED,
    SYSTEM_MESSAGE_POSTED,
    MEMBERS_SYNCED;

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
