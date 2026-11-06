package network.foodsharing.automation.domain;

/** Outcome of one store-request-policy evaluation. */
public enum RequestDecision {
    APPROVED, DECLINED, SKIPPED, FAILED
}
