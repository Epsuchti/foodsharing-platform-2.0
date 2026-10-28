package network.foodsharing.automation.domain;

/** Outcome of a short-notice sign-out. */
public enum CancellationStatus {
    OPEN, REPLACED, NOT_REPLACED, REJOINED, UNKNOWN
}
