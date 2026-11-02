package network.foodsharing.automation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.automation.domain.LinkedStoreWindowMode;

/**
 * Linked-store rules against a shared gap list (decision 151, D7). A link rule asks for a pickup in one of its stores
 * within its window; the gap list forbids two pickups closer than the gap in its stores. A rule's reach is the largest
 * calendar gap a matching pair can have: same day = 0, otherwise {@code max(before, after) − 1}. When the reach is
 * less than the gap and the rule's owner and at least one of its stores are in the list, the two rules contradict each
 * other: blocking when every store of the rule is in the list (no way out), otherwise a warning (another store of the
 * rule still works).
 */
public final class GapLinkConflicts {

    /** A link rule as seen from its owner (own rule or mirror). */
    public record Link(UUID ownerStoreId, List<UUID> storeIds, LinkedStoreWindowMode windowMode, int windowDaysBefore,
                int windowDaysAfter) {
    }

    /** {@code storeId}: the rule's owner; {@code otherStoreId}: a listed store of the rule. */
    public record Conflict(UUID storeId, UUID otherStoreId, boolean blocking) {
    }

    private GapLinkConflicts() {
    }

    static int reach(LinkedStoreWindowMode mode, int before, int after) {
        if (mode != LinkedStoreWindowMode.LAST_DAYS) {
            return 0;
        }
        return Math.max(0, Math.max(before, after) - 1);
    }

    /** Every conflict of the rules with the list ({@code listStoreIds} includes the saving store). */
    static List<Conflict> find(Set<UUID> listStoreIds, int minimumGapDays, Collection<Link> links) {
        List<Conflict> conflicts = new ArrayList<>();
        if (listStoreIds.size() < 2) {
            return conflicts;
        }
        for (Link link : links) {
            if (!listStoreIds.contains(link.ownerStoreId()) || link.storeIds().isEmpty()) {
                continue;
            }
            Optional<UUID> listed = link.storeIds().stream().filter(listStoreIds::contains).findFirst();
            if (listed.isEmpty()
                    || reach(link.windowMode(), link.windowDaysBefore(), link.windowDaysAfter()) >= minimumGapDays) {
                continue;
            }
            conflicts.add(new Conflict(link.ownerStoreId(), listed.get(), listStoreIds.containsAll(link.storeIds())));
        }
        return conflicts;
    }

    static Optional<Conflict> firstBlocking(Set<UUID> listStoreIds, int minimumGapDays, Collection<Link> links) {
        return find(listStoreIds, minimumGapDays, links).stream().filter(Conflict::blocking).findFirst();
    }
}
