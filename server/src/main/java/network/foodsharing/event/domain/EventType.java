package network.foodsharing.event.domain;

/** Event type: OFFLINE needs a location, ONLINE an https link. */
public enum EventType {
    OFFLINE,
    ONLINE,
    OTHER
}
