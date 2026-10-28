package network.foodsharing.basket.domain;

/** State of a basket request (upstream fs_basket_anfrage.status). */
public enum BasketRequestStatus {
    REQUESTED,
    READ,
    PICKED_UP,
    DENIED,
    NOT_PICKED_UP,
    WITHDRAWN
}
