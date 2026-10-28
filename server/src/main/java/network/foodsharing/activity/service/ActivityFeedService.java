package network.foodsharing.activity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.activity.domain.ActivityType;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.user.service.ActivityContribution;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard activity feed (A19): the newest items of every {@link ActivitySource} the user did not hide
 * ({@code user_account.activity_filters}), merged by time. Paging takes {@code (page + 1) * size} items of every
 * source and slices the merged list (the feed is shallow; upstream pages the same way per source).
 */
@Service
@Transactional(readOnly = true)
public class ActivityFeedService {

    public static final int MAX_SIZE = 50;

    /** One feed entry. */
    public record ActivityItem(ActivityType type, Instant at, String title, String excerpt, String link,
                               UUID authorId, UUID sourceId, String sourceName) {
    }

    /** A source of feed entries (implemented in this module on top of the other modules' services). */
    public interface ActivitySource {

        ActivityType type();

        List<ActivityItem> latest(UUID userId, Instant before, int limit);
    }

    private final List<ActivitySource> sources;
    private final CurrentActorService actors;
    private final UserService users;
    private final Clock clock;

    public ActivityFeedService(List<ActivitySource> sources, List<ActivityContribution> contributions,
                               CurrentActorService actors, UserService users, Clock clock) {
        List<ActivitySource> all = new ArrayList<>(sources);
        contributions.forEach(c -> all.add(adapt(c)));
        this.sources = List.copyOf(all);
        this.actors = actors;
        this.users = users;
        this.clock = clock;
    }

    public Page<ActivityItem> feed(int page, int size) {
        UUID userId = actors.requireUserId();
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), MAX_SIZE);
        Set<ActivityType> excluded = EnumSet.noneOf(ActivityType.class);
        users.activityFilters(userId).forEach(name -> {
            try {
                excluded.add(ActivityType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // unknown stored value
            }
        });
        return merge(sources, excluded, userId, clock.instant(), p, s);
    }

    /** Wraps a contribution of a module the activity module may not depend on (A38) as a source. */
    static ActivitySource adapt(ActivityContribution contribution) {
        ActivityType type = ActivityType.valueOf(contribution.type());
        return new ActivitySource() {
            @Override
            public ActivityType type() {
                return type;
            }

            @Override
            public List<ActivityItem> latest(UUID userId, Instant before, int limit) {
                return contribution.latest(userId, before, limit).stream().map(i -> new ActivityItem(type, i.at(),
                        i.title(), excerpt(i.text()), i.link(), i.authorId(), i.sourceId(), i.sourceName())).toList();
            }
        };
    }

    /** Merges the sources newest first and slices one page. */
    static Page<ActivityItem> merge(List<ActivitySource> sources, Set<ActivityType> excluded, UUID userId,
                                    Instant before, int page, int size) {
        int limit = (page + 1) * size;
        List<ActivityItem> all = new ArrayList<>();
        for (ActivitySource source : sources) {
            if (!excluded.contains(source.type())) {
                all.addAll(source.latest(userId, before, limit));
            }
        }
        all.sort(Comparator.comparing(ActivityItem::at).reversed());
        int from = Math.min(page * size, all.size());
        List<ActivityItem> slice = all.subList(from, Math.min(all.size(), from + size));
        return new PageImpl<>(List.copyOf(slice), PageRequest.of(page, size), all.size());
    }

    static String excerpt(String text) {
        if (text == null) {
            return null;
        }
        String flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() <= 300 ? flat : flat.substring(0, 299) + "…";
    }
}
