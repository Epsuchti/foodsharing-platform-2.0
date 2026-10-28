package network.foodsharing.activity.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import network.foodsharing.activity.domain.ActivityType;
import network.foodsharing.activity.service.ActivityFeedService.ActivityItem;
import network.foodsharing.activity.service.ActivityFeedService.ActivitySource;
import network.foodsharing.forum.service.ForumFeedService;
import network.foodsharing.mailbox.service.MailboxFeedService;
import org.springframework.stereotype.Component;

/** Forum and mailbox feed sources (A19): forum posts of my forums, new mails of my mailboxes (subject only). */
final class CommunicationActivitySources {

    private CommunicationActivitySources() {
    }

    @Component
    static class Forum implements ActivitySource {

        private final ForumFeedService forum;

        Forum(ForumFeedService forum) {
            this.forum = forum;
        }

        @Override
        public ActivityType type() {
            return ActivityType.FORUM;
        }

        @Override
        public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
            return forum.latest(userId, before, limit).stream().map(p -> new ActivityItem(type(), p.createdAt(),
                    p.threadTitle(), ActivityFeedService.excerpt(p.body()), "/forum/threads/" + p.threadId(),
                    p.authorId(), p.threadId(), p.threadTitle())).toList();
        }
    }

    @Component
    static class Mailbox implements ActivitySource {

        private final MailboxFeedService mailboxes;

        Mailbox(MailboxFeedService mailboxes) {
            this.mailboxes = mailboxes;
        }

        @Override
        public ActivityType type() {
            return ActivityType.MAILBOX;
        }

        @Override
        public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
            return mailboxes.latest(userId, before, limit).stream().map(m -> new ActivityItem(type(), m.receivedAt(),
                    m.subject(), m.fromName() != null && !m.fromName().isBlank() ? m.fromName() : m.fromAddress(),
                    "/mailboxes/" + m.mailboxId(), null, m.mailboxId(), m.mailboxName())).toList();
        }
    }
}
