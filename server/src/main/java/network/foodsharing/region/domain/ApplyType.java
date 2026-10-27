package network.foodsharing.region.domain;

/** Who may join a working group. Upstream REQUIRES_PROPERTIES is modelled as EVERYBODY + requirement fields. */
public enum ApplyType {
    NOBODY,
    EVERYBODY,
    OPEN;
}
