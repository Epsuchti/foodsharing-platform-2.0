package network.foodsharing.automation.domain;

/**
 * Outcome of one pickup-policy evaluation (DEFERRED writes no audit; WOULD_* are read-only legacy values, decision
 * 167).
 */
public enum PickupPolicyDecision {
    CONFIRMED, DECLINED, WOULD_CONFIRM, WOULD_DECLINE, SKIPPED, DEFERRED, FAILED
}
