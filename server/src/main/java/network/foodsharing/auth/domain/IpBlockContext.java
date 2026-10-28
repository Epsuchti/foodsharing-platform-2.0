package network.foodsharing.auth.domain;

/** Where an IP block applies (C12); ALL covers login and registration. */
public enum IpBlockContext {
    LOGIN,
    REGISTRATION,
    ALL;

    public boolean covers(IpBlockContext requested) {
        return this == ALL || this == requested;
    }
}
