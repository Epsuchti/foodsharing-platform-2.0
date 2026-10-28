package network.foodsharing.poll.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of polls (A40): the vote record only says who voted (counts live on the option
 * values), so it is removed; authored polls stay (anonymised author).
 */
@Component
public class PollAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public PollAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "PollVote", "userId", userId);
    }

    @Override
    public String section() {
        return "polls";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authored", UserRows.read(em, "Poll", "authorId", userId, "regionId", "createdAt"));
        data.put("votes", UserRows.read(em, "PollVote", "userId", userId, "pollId", "votedAt"));
        return data;
    }
}
