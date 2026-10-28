package network.foodsharing.automation.domain;

/** Outcome of one pickup-policy evaluation (WOULD_* in dry run; DEFERRED writes no audit). */
public enum PickupPolicyDecision {
    CONFIRMED, DECLINED, WOULD_CONFIRM, WOULD_DECLINE, SKIPPED, DEFERRED, FAILED
}
