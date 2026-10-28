package network.foodsharing.messaging.service;

import java.util.UUID;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.messaging.repository.ConversationMemberRepository;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** Conversation history: its members (ORGA always). */
@Component
public class MessagingHistoryAccessRule implements HistoryAccessRule {

    private final ConversationMemberRepository members;

    public MessagingHistoryAccessRule(ConversationMemberRepository members) {
        this.members = members;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.CONVERSATION;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return members.existsByConversationIdAndUserId(subjectId, actor.userId());
    }
}
