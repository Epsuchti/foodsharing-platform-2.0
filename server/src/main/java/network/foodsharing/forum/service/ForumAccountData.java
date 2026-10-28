package network.foodsharing.forum.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of the forum (A40): threads and posts stay (anonymised author); follows and reactions
 * are removed.
 */
@Component
public class ForumAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public ForumAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "ForumFollow", "userId", userId);
        UserRows.delete(em, "ForumThreadFollow", "userId", userId);
        UserRows.delete(em, "ForumPostReaction", "userId", userId);
    }

    @Override
    public String section() {
        return "forum";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threads", UserRows.read(em, "ForumThread", "authorId", userId, "regionId", "title", "createdAt"));
        data.put("posts", UserRows.read(em, "ForumPost", "authorId", userId, "threadId", "body", "createdAt", "editedAt"));
        data.put("regionFollows", UserRows.read(em, "ForumFollow", "userId", userId, "regionId"));
        data.put("threadFollows", UserRows.read(em, "ForumThreadFollow", "userId", userId, "threadId"));
        data.put("reactions", UserRows.read(em, "ForumPostReaction", "userId", userId, "postId", "reactionKey"));
        return data;
    }
}
