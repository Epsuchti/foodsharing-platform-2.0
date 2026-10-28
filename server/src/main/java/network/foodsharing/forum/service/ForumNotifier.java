package network.foodsharing.forum.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import network.foodsharing.common.realtime.RealtimeEvent;
import network.foodsharing.common.realtime.RealtimeEventType;
import network.foodsharing.common.realtime.RealtimePublisher;
import network.foodsharing.config.AppProperties;
import network.foodsharing.forum.domain.ForumPost;
import network.foodsharing.forum.domain.ForumThread;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.repository.ForumFollowRepository;
import network.foodsharing.forum.repository.ForumThreadFollowRepository;
import network.foodsharing.notification.domain.NotificationType;
import network.foodsharing.notification.mail.service.MailDraft;
import network.foodsharing.notification.mail.service.MailOutboxService;
import network.foodsharing.notification.service.NotificationRequest;
import network.foodsharing.notification.service.NotificationService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/**
 * Forum bells, follower mails and realtime events (B6). Bell identifiers: {@code FORUM_THREAD_TO_ACTIVATE-<thread>},
 * {@code FORUM_NEW_THREAD-<thread>}, {@code FORUM_POST-<thread>} (one grouped bell per thread),
 * {@code FORUM_MENTION-<post>}, {@code POST_HIDDEN-<post>}.
 */
@Component
public class ForumNotifier {

    /** Mention token in post bodies: {@code @[Display Name](user:<uuid>)}. */
    static final Pattern MENTION = Pattern.compile(
            "@\\[[^\\]\\n]{1,200}\\]\\(user:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");
    static final String MAIL_FORUM_POST = "notification/FORUM_POST";
    static final String MAIL_THREAD_TO_ACTIVATE = "notification/FORUM_THREAD_TO_ACTIVATE";

    private final NotificationService notifications;
    private final MailOutboxService mails;
    private final RealtimePublisher realtime;
    private final ForumThreadFollowRepository threadFollows;
    private final ForumFollowRepository forumFollows;
    private final UserService users;
    private final AppProperties properties;
    private final Clock clock;

    public ForumNotifier(NotificationService notifications, MailOutboxService mails, RealtimePublisher realtime,
                         ForumThreadFollowRepository threadFollows, ForumFollowRepository forumFollows,
                         UserService users, AppProperties properties, Clock clock) {
        this.notifications = notifications;
        this.mails = mails;
        this.realtime = realtime;
        this.threadFollows = threadFollows;
        this.forumFollows = forumFollows;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    public static String threadLink(UUID threadId) {
        return "/forum/threads/" + threadId;
    }

    public static Set<UUID> mentionedUserIds(String body) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (body == null) {
            return ids;
        }
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find()) {
            ids.add(UUID.fromString(matcher.group(1).toLowerCase()));
        }
        return ids;
    }

    /** A new thread waits for activation: bell (+ mail per settings) to the moderators. */
    void threadToActivate(ForumThread thread, Set<UUID> moderators, String authorName) {
        if (moderators.isEmpty()) {
            return;
        }
        notifications.notify(NotificationRequest.to(moderators, NotificationType.FORUM_THREAD_TO_ACTIVATE)
                .param("title", thread.getTitle()).param("author", authorName)
                .link(threadLink(thread.getId())).mail(MAIL_THREAD_TO_ACTIVATE)
                .identifier("FORUM_THREAD_TO_ACTIVATE-" + thread.getId()).build());
    }

    /** A thread became visible: bell to the forum's followers except the author; activation bells go away. */
    void threadActive(ForumThread thread, String authorName) {
        notifications.removeBells("FORUM_THREAD_TO_ACTIVATE-" + thread.getId());
        Set<UUID> recipients = new LinkedHashSet<>();
        forumFollows.findByRegionIdAndAmbassadorForum(thread.getRegionId(), thread.isAmbassadorForum())
                .forEach(follow -> recipients.add(follow.getUserId()));
        recipients.remove(thread.getAuthorId());
        if (!recipients.isEmpty()) {
            notifications.notify(NotificationRequest.to(recipients, NotificationType.FORUM_NEW_THREAD)
                    .param("title", thread.getTitle()).param("author", authorName)
                    .link(threadLink(thread.getId())).identifier("FORUM_NEW_THREAD-" + thread.getId()).build());
        }
    }

    void threadRemoved(UUID threadId) {
        notifications.removeBells("FORUM_THREAD_TO_ACTIVATE-" + threadId);
        notifications.removeBells("FORUM_NEW_THREAD-" + threadId);
        notifications.removeBells("FORUM_POST-" + threadId);
    }

    /**
     * New post: grouped bell to bell followers, mail to mail followers (their explicit choice, independent of the
     * FORUM category mail switch), mention bells; the author never notifies themself.
     */
    void postCreated(ForumThread thread, ForumPost post, String authorName, Set<UUID> mayRead) {
        List<ForumThreadFollow> follows = threadFollows.findByThreadId(thread.getId());
        Set<UUID> bell = new LinkedHashSet<>();
        Set<UUID> mail = new LinkedHashSet<>();
        for (ForumThreadFollow follow : follows) {
            if (follow.getUserId().equals(post.getAuthorId())) {
                continue;
            }
            if (follow.isBell()) {
                bell.add(follow.getUserId());
            }
            if (follow.isMail()) {
                mail.add(follow.getUserId());
            }
        }
        String identifier = "FORUM_POST-" + thread.getId();
        if (!bell.isEmpty()) {
            notifications.removeBells(identifier);
            notifications.notify(NotificationRequest.to(bell, NotificationType.FORUM_POST)
                    .param("title", thread.getTitle()).param("author", authorName)
                    .link(threadLink(thread.getId()) + "#post-" + post.getId()).identifier(identifier).build());
        }
        if (!mail.isEmpty()) {
            mailFollowers(thread, post, authorName, mail);
        }
        Set<UUID> mentioned = mentionedUserIds(post.getBody());
        mentioned.remove(post.getAuthorId());
        mentioned.retainAll(mayRead);
        if (!mentioned.isEmpty()) {
            notifications.notify(NotificationRequest.to(mentioned, NotificationType.FORUM_MENTION)
                    .param("title", thread.getTitle()).param("author", authorName)
                    .link(threadLink(thread.getId()) + "#post-" + post.getId())
                    .identifier("FORUM_MENTION-" + post.getId()).build());
        }
        Set<UUID> live = new LinkedHashSet<>(bell);
        live.addAll(mail);
        live.add(post.getAuthorId());
        publish(thread, post.getId(), "CREATED", live);
    }

    private void mailFollowers(ForumThread thread, ForumPost post, String authorName, Set<UUID> recipients) {
        String link = properties.getBaseUrl() + threadLink(thread.getId()) + "#post-" + post.getId();
        for (User user : users.findActive(recipients)) {
            if (user.getEmail() == null) {
                continue;
            }
            Map<String, Object> variables = new HashMap<>();
            variables.put("displayName", user.getDisplayName());
            variables.put("title", thread.getTitle());
            variables.put("author", authorName);
            variables.put("body", abbreviate(post.getBody(), 2000));
            variables.put("link", link);
            mails.enqueue(new MailDraft(user.getEmail(), MAIL_FORUM_POST, user.getPreferredLanguage(), variables,
                    user.getId(), "FORUM", "FORUM_POST-" + thread.getId()));
        }
    }

    void mentionsRemoved(UUID postId) {
        notifications.removeBells("FORUM_MENTION-" + postId);
    }

    void postHidden(ForumThread thread, ForumPost post, String reason) {
        notifications.notify(NotificationRequest.to(Set.of(post.getAuthorId()), NotificationType.POST_HIDDEN)
                .param("title", thread.getTitle()).param("reason", reason)
                .link(threadLink(thread.getId()) + "#post-" + post.getId())
                .identifier("POST_HIDDEN-" + post.getId()).build());
    }

    void postUnhidden(UUID postId) {
        notifications.removeBells("POST_HIDDEN-" + postId);
    }

    /**
     * Realtime {@code FORUM_POST} to the thread's followers (+ extra users): {@code {threadId, regionId, postId,
     * change}} with change CREATED, EDITED, DELETED, HIDDEN, UNHIDDEN or REACTION.
     */
    void publish(ForumThread thread, UUID postId, String change, Set<UUID> extra) {
        Set<UUID> recipients = new LinkedHashSet<>(extra);
        threadFollows.findByThreadId(thread.getId()).forEach(follow -> recipients.add(follow.getUserId()));
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("threadId", thread.getId().toString());
        payload.put("regionId", thread.getRegionId().toString());
        if (postId != null) {
            payload.put("postId", postId.toString());
        }
        payload.put("change", change);
        realtime.toUsers(recipients, new RealtimeEvent(RealtimeEventType.FORUM_POST, clock.instant(), payload));
    }

    static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
