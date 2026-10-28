package network.foodsharing.search.domain;

import java.util.UUID;

/** One search hit / quick-access entry ({@code kind} null for conversations). */
public record SearchHitRow(SearchKind kind, UUID entityId, String title, String subtitle, String link,
                           String imageUrl) {
}
