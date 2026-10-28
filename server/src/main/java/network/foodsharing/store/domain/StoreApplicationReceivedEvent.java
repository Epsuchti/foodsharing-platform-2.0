package network.foodsharing.store.domain;

import java.util.UUID;

/** A user applied to a store team (A24 trigger of the request policy); published inside the apply transaction. */
public record StoreApplicationReceivedEvent(UUID storeId, UUID userId) {
}
