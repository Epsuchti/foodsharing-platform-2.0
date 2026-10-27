package network.foodsharing.store.domain;

/** Cooperation status of a store chain. */
public enum StoreChainStatus {
    NOT_COOPERATING,
    WAITING,
    COOPERATING,
    INSUFFICIENT_BRANCHES,
    CLOSED
}
