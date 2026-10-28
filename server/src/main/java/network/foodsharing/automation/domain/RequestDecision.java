package network.foodsharing.automation.domain;

/** Outcome of one store-request-policy evaluation. */
public enum RequestDecision {
    APPROVED, DECLINED, WOULD_APPROVE, WOULD_DECLINE, SKIPPED, FAILED
}
