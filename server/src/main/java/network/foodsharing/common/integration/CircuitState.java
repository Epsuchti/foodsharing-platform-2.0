package network.foodsharing.common.integration;

/** Circuit breaker state: OPEN skips calls until the next probe, HALF_OPEN lets one probe through. */
public enum CircuitState {
    CLOSED, OPEN, HALF_OPEN
}
