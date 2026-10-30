package network.foodsharing.activity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.activity.domain.ActivityType;
import network.foodsharing.activity.service.ActivityFeedService.ActivityItem;
import network.foodsharing.activity.service.ActivityFeedService.ActivitySource;
import org.junit.jupiter.api.Test;

/** A19: sources merged newest first, hidden types skipped, pages sliced, excerpts shortened, ties ordered stably. */
class ActivityFeedServiceTest {

    private static ActivityItem item(ActivityType type, Instant at, String title) {
        return new ActivityItem(UUID.randomUUID(), type, at, title, null, "/x", null, null, null);
    }

    private static ActivitySource source(ActivityType type, String... times) {
        return source(type, java.util.Arrays.stream(times).map(t -> item(type, Instant.parse(t), "t")).toList());
    }

    /** A source that honours the documented contract: newest first, ties by id descending, at most {@code limit}. */
    private static ActivitySource source(ActivityType type, List<ActivityItem> items) {
        return new ActivitySource() {
            @Override
            public ActivityType type() {
                return type;
            }

            @Override
            public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
                return items.stream().sorted(ActivityFeedService.ORDER).limit(limit).toList();
            }
        };
    }

    @Test
    void mergesByTimeSkipsHiddenTypesAndPages() {
        List<ActivitySource> sources = List.of(
                source(ActivityType.FSP_WALL, "2026-10-20T10:00:00Z", "2026-10-18T10:00:00Z"),
                source(ActivityType.STORE_WALL, "2026-10-19T10:00:00Z", "2026-10-17T10:00:00Z"),
                source(ActivityType.EVENT_WALL, "2026-10-21T10:00:00Z"));
        Instant now = Instant.parse("2026-10-28T00:00:00Z");
        var first = ActivityFeedService.merge(sources, Set.of(ActivityType.EVENT_WALL), UUID.randomUUID(), now, 0, 3);
        assertThat(first.getContent()).extracting(ActivityItem::type).containsExactly(ActivityType.FSP_WALL,
                ActivityType.STORE_WALL, ActivityType.FSP_WALL);
        var second = ActivityFeedService.merge(sources, Set.of(ActivityType.EVENT_WALL), UUID.randomUUID(), now, 1,
                3);
        assertThat(second.getContent()).extracting(ActivityItem::at)
                .containsExactly(Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    void tiedItemsAcrossAPageBoundaryAppearExactlyOnce() {
        Instant tie = Instant.parse("2026-10-27T09:00:00Z");
        List<ActivityItem> store = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            store.add(item(ActivityType.STORE_WALL, tie, "Migros City"));
        }
        store.add(item(ActivityType.STORE_WALL, tie.minusSeconds(60), "older"));
        List<ActivityItem> forum = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            forum.add(item(ActivityType.FORUM, tie, "thread"));
        }
        forum.add(item(ActivityType.FORUM, tie.plusSeconds(60), "newer"));
        // the order the items were created in must not matter
        Collections.shuffle(store, new Random(7));
        Collections.shuffle(forum, new Random(11));
        List<ActivitySource> sources = List.of(source(ActivityType.STORE_WALL, store),
                source(ActivityType.FORUM, forum));
        Instant now = Instant.parse("2026-10-28T00:00:00Z");

        // pages of 4 put the boundaries inside both tied groups
        List<UUID> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            ActivityFeedService.merge(sources, Set.of(), UUID.randomUUID(), now, page, 4).getContent()
                    .forEach(i -> seen.add(i.id()));
        }
        assertThat(seen).hasSize(14).doesNotHaveDuplicates();
        Set<UUID> all = new HashSet<>();
        store.forEach(i -> all.add(i.id()));
        forum.forEach(i -> all.add(i.id()));
        assertThat(new HashSet<>(seen)).isEqualTo(all);

        // the same request always answers the same page
        assertThat(ActivityFeedService.merge(sources, Set.of(), UUID.randomUUID(), now, 1, 4).getContent())
                .isEqualTo(ActivityFeedService.merge(sources, Set.of(), UUID.randomUUID(), now, 1, 4).getContent());
        // ties: type name first (FORUM before STORE_WALL), then id descending
        List<ActivityItem> merged = ActivityFeedService.merge(sources, Set.of(), UUID.randomUUID(), now, 0, 14)
                .getContent();
        assertThat(merged.get(0).title()).isEqualTo("newer");
        assertThat(merged.subList(1, 6)).extracting(ActivityItem::type).containsOnly(ActivityType.FORUM);
        assertThat(merged.subList(6, 13)).extracting(ActivityItem::type).containsOnly(ActivityType.STORE_WALL);
        assertThat(merged.subList(6, 13).stream().map(i -> i.id().toString()).toList())
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(merged.get(13).title()).isEqualTo("older");
    }

    @Test
    void idsCompareAsUnsignedTextLikeTheDatabase() {
        ActivityItem low = new ActivityItem(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                ActivityType.FORUM, Instant.EPOCH, "a", null, "/", null, null, null);
        ActivityItem high = new ActivityItem(UUID.fromString("f0000000-0000-4000-8000-000000000001"),
                ActivityType.FORUM, Instant.EPOCH, "b", null, "/", null, null, null);
        // UUID.compareTo is signed (f… < 0…); ORDER BY id DESC in PostgreSQL/H2 puts f… first, and so does the feed
        assertThat(high.id().compareTo(low.id())).isNegative();
        assertThat(ActivityFeedService.ORDER.compare(high, low)).isNegative();
    }

    @Test
    void contributionsOfOtherModulesBecomeSourcesOfTheirType() {
        UUID owner = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var contribution = new network.foodsharing.user.service.ActivityContribution() {
            @Override
            public String type() {
                return "BUDDY_WALL";
            }

            @Override
            public List<Item> latest(UUID userId, Instant before, int limit) {
                return List.of(new Item(postId, Instant.parse("2026-10-22T10:00:00Z"), "Buddy", " hi\n there ",
                        "/profile/" + owner, owner, owner, "Buddy"));
            }
        };
        ActivitySource source = ActivityFeedService.adapt(contribution);
        assertThat(source.type()).isEqualTo(ActivityType.BUDDY_WALL);
        var page = ActivityFeedService.merge(List.of(source), Set.of(), UUID.randomUUID(),
                Instant.parse("2026-10-28T00:00:00Z"), 0, 10);
        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(postId);
            assertThat(item.excerpt()).isEqualTo("hi there");
            assertThat(item.link()).isEqualTo("/profile/" + owner);
        });
        assertThat(ActivityFeedService.merge(List.of(source), Set.of(ActivityType.BUDDY_WALL), UUID.randomUUID(),
                Instant.parse("2026-10-28T00:00:00Z"), 0, 10).getContent()).isEmpty();
    }

    @Test
    void excerptsAreFlattenedAndCut() {
        assertThat(ActivityFeedService.excerpt(" a\n\n b ")).isEqualTo("a b");
        assertThat(ActivityFeedService.excerpt("x".repeat(400))).hasSize(300);
        assertThat(ActivityFeedService.excerpt(null)).isNull();
    }
}
