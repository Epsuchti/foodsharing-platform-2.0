package network.foodsharing.messaging.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.messaging.repository.ConversationMemberRepository;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/**
 * Conversation history: its members and its moderators (ORGA always). The original text of edited and deleted
 * messages (old/new value, reason) is visible to the moderators only ({@link ChatModeration}, A33 amended).
 */
@Component
public class MessagingHistoryAccessRule implements HistoryAccessRule {

    private final ConversationMemberRepository members;
    private final ChatModeration moderation;

    public MessagingHistoryAccessRule(ConversationMemberRepository members, ChatModeration moderation) {
        this.members = members;
        this.moderation = moderation;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.CONVERSATION;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return members.existsByConversationIdAndUserId(subjectId, actor.userId())
                || moderation.isModerator(actor, subjectId);
    }

    @Override
    public boolean mayReadValues(SessionPrincipal actor, UUID subjectId) {
        return moderation.isModerator(actor, subjectId);
    }
}
