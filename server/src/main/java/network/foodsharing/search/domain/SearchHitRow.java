package network.foodsharing.search.domain;

import java.time.Instant;
import java.util.UUID;

/** One search hit / quick-access entry ({@code kind} null for conversations). */
public record SearchHitRow(SearchKind kind, UUID entityId, String title, String subtitle, String link,
                           String imageUrl, Instant startsAt) {

    /** A hit without a start time (everything but events). */
    public SearchHitRow(SearchKind kind, UUID entityId, String title, String subtitle, String link, String imageUrl) {
        this(kind, entityId, title, subtitle, link, imageUrl, null);
    }
}
