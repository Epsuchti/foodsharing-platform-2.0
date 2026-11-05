package network.foodsharing.automation.domain;

/** Outcome of one pickup-policy evaluation (DEFERRED writes no audit). */
public enum PickupPolicyDecision {
    CONFIRMED, DECLINED, SKIPPED, DEFERRED, FAILED
}
