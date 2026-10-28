package network.foodsharing.messaging.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of conversations (A40): sent messages stay (anonymised sender); group, store and region
 * memberships are removed, direct conversations keep the member row so the partner's chat stays intact.
 */
@Component
public class MessagingAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public MessagingAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        em.createQuery("delete from ConversationMember m where m.userId = :userId and m.conversationId in "
                + "(select c.id from Conversation c where c.kind <> network.foodsharing.messaging.domain.ConversationKind.DIRECT)")
                .setParameter("userId", userId).executeUpdate();
    }

    @Override
    public String section() {
        return "messages";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberships", UserRows.read(em, "ConversationMember", "userId", userId, "conversationId", "joinedAt", "lastReadSeq"));
        data.put("sentMessages", UserRows.read(em, "ConversationMessage", "senderId", userId, "conversationId", "body", "sentAt"));
        return data;
    }
}
