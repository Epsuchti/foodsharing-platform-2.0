package network.foodsharing.forum.service;

import java.util.UUID;
import network.foodsharing.forum.repository.ForumThreadRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Component;

/** THREAD history: readable by everyone who may see the thread (moderation actions are filtered by the log). */
@Component
public class ForumHistoryAccessRule implements HistoryAccessRule {

    private final ForumThreadRepository threads;
    private final ForumPermissionService access;

    public ForumHistoryAccessRule(ForumThreadRepository threads, ForumPermissionService access) {
        this.threads = threads;
        this.access = access;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.THREAD;
    }

    @Override
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        return threads.findById(subjectId).map(thread -> {
            ForumRules.Forum forum = access.forum(thread);
            ForumRules.Actor facts = access.actor(actor, thread.getRegionId());
            return ForumRules.maySeeThread(facts, forum, thread.isActive(), thread.getAuthorId())
                    && (thread.isActive() || ForumRules.mayModerate(facts, forum));
        }).orElse(false);
    }
}
