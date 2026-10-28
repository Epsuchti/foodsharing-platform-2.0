package network.foodsharing.store.domain;

import java.util.UUID;

/**
 * Published by the store module after every team status or manager change of one user (A33); {@code newStatus}
 * {@code null} = no membership any more. Listeners (store chats) run synchronously in the same transaction.
 */
public record StoreTeamChangedEvent(UUID storeId, UUID userId, StoreMembershipStatus oldStatus,
                                    StoreMembershipStatus newStatus, boolean manager) {
}
