package network.foodsharing.user.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Activity feed entries contributed by modules the activity module may not depend on (A38: e.g. profile buddy
 * walls). The activity module merges every bean into the dashboard feed (pattern A22; default: none).
 */
public interface ActivityContribution {

    /** One entry; {@code link} is an SPA route. */
    record Item(Instant at, String title, String text, String link, UUID authorId, UUID sourceId,
                String sourceName) {
    }

    /** Name of the {@code ActivityType} the entries belong to (also the filter key). */
    String type();

    /** The newest entries for {@code userId} created before {@code before}, newest first. */
    List<Item> latest(UUID userId, Instant before, int limit);
}
