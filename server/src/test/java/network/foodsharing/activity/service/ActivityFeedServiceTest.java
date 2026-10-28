package network.foodsharing.activity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.activity.domain.ActivityType;
import network.foodsharing.activity.service.ActivityFeedService.ActivityItem;
import network.foodsharing.activity.service.ActivityFeedService.ActivitySource;
import org.junit.jupiter.api.Test;

/** A19: sources merged newest first, hidden types skipped, pages sliced, excerpts shortened. */
class ActivityFeedServiceTest {

    private static ActivitySource source(ActivityType type, String... times) {
        return new ActivitySource() {
            @Override
            public ActivityType type() {
                return type;
            }

            @Override
            public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
                return java.util.Arrays.stream(times).map(t -> new ActivityItem(type, Instant.parse(t), "t", null,
                        "/x", null, null, null)).limit(limit).toList();
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
    void excerptsAreFlattenedAndCut() {
        assertThat(ActivityFeedService.excerpt(" a\n\n b ")).isEqualTo("a b");
        assertThat(ActivityFeedService.excerpt("x".repeat(400))).hasSize(300);
        assertThat(ActivityFeedService.excerpt(null)).isNull();
    }
}
