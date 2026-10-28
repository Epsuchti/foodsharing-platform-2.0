package network.foodsharing.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.forum.service.ForumFeedService;
import network.foodsharing.mailbox.service.MailboxFeedService;
import network.foodsharing.profile.service.BuddyWallActivity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** A19: forum, mailbox and buddy-wall feed sources against the seed (visibility and ordering). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:activity-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false"})
@ActiveProfiles("e2e")
class ActivitySourcesIT {

    private static final Instant FAR = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private ForumFeedService forum;

    @Autowired
    private MailboxFeedService mailboxes;

    @Autowired
    private BuddyWallActivity buddyWall;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void forumPostsOnlyFromAccessibleForumsNewestFirst() {
        UUID author = jdbc.queryForObject("select p.author_id from forum_post p join forum_thread t on t.id = p.thread_id"
                + " where t.ambassador_forum = false and p.deleted_at is null fetch first 1 rows only", UUID.class);
        Set<UUID> memberRegions = Set.copyOf(jdbc.queryForList(
                "select region_id from region_membership where user_id = ? and active = true", UUID.class, author));
        var posts = forum.latest(author, FAR, 50);
        assertThat(posts).isNotEmpty();
        assertThat(posts).allSatisfy(p -> assertThat(p.body()).isNotNull());
        List<Instant> times = posts.stream().map(ForumFeedService.FeedPost::createdAt).toList();
        assertThat(times).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        Set<UUID> hidden = Set.copyOf(jdbc.queryForList(
                "select id from forum_post where hidden_at is not null or deleted_at is not null", UUID.class));
        assertThat(posts).noneMatch(p -> hidden.contains(p.id()));
        Set<UUID> adminRegions = Set.copyOf(jdbc.queryForList(
                "select region_id from region_admin where user_id = ?", UUID.class, author));
        Set<UUID> ambassadorThreads = Set.copyOf(jdbc.queryForList(
                "select id from forum_thread where ambassador_forum = true", UUID.class));
        assertThat(posts).allMatch(p -> ambassadorThreads.contains(p.threadId())
                ? adminRegions.contains(p.regionId()) : memberRegions.contains(p.regionId()));
        assertThat(forum.latest(author, times.getLast(), 50)).noneMatch(p -> !p.createdAt().isBefore(times.getLast()));
        assertThat(forum.latest(UUID.randomUUID(), FAR, 10)).isEmpty();
    }

    @Test
    void mailboxMembersSeeInboxSubjects() {
        UUID member = jdbc.queryForObject("select m.user_id from mailbox_member m where exists (select 1 from "
                + "mail_message x where x.mailbox_id = m.mailbox_id and x.folder = 'INBOX') fetch first 1 rows only",
                UUID.class);
        var mails = mailboxes.latest(member, FAR, 50);
        assertThat(mails).isNotEmpty();
        Set<UUID> inbox = Set.copyOf(jdbc.queryForList("select id from mail_message where folder = 'INBOX'",
                UUID.class));
        assertThat(mails).allMatch(m -> inbox.contains(m.id()));
        assertThat(mailboxes.latest(UUID.randomUUID(), FAR, 10)).isEmpty();
    }

    @Test
    void buddyWallPostsOfConfirmedBuddies() {
        var pair = jdbc.queryForMap("select user_id, buddy_id from buddy where status = 'CONFIRMED' "
                + "fetch first 1 rows only");
        UUID me = (UUID) pair.get("USER_ID");
        UUID buddy = (UUID) pair.get("BUDDY_ID");
        jdbc.update("insert into user_wall_post (id, version, created_at, updated_at, user_id, author_id, body) "
                + "values (?, 0, ?, ?, ?, ?, 'Hallo Buddy')", UUID.randomUUID(),
                java.sql.Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")),
                java.sql.Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")), buddy, buddy);
        var items = buddyWall.latest(me, FAR, 20);
        assertThat(items).isNotEmpty();
        assertThat(items.getFirst().text()).isEqualTo("Hallo Buddy");
        assertThat(items.getFirst().link()).isEqualTo("/profile/" + buddy);
        Set<UUID> confirmed = jdbc.queryForList("select buddy_id from buddy where user_id = ? and status = 'CONFIRMED'",
                UUID.class, me).stream().collect(Collectors.toSet());
        assertThat(items).allMatch(i -> confirmed.contains(i.sourceId()));
        assertThat(buddyWall.latest(UUID.randomUUID(), FAR, 10)).isEmpty();
    }
}
