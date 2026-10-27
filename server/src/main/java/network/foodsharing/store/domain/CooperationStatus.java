package network.foodsharing.store.domain;

/** Cooperation status of a store (upstream CooperationStatus). */
public enum CooperationStatus {
    UNCLEAR,
    NO_CONTACT,
    IN_NEGOTIATION,
    DOES_NOT_WANT_TO_WORK_WITH_US,
    COOPERATION_ESTABLISHED,
    GIVES_TO_OTHER_CHARITY,
    PERMANENTLY_CLOSED
}
