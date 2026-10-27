package network.foodsharing.auth.domain;

/** How a session was created (metadata {@code method} of AuthAction.LOGIN). */
public enum LoginMethod {
    GOOGLE, EMAIL, PASSKEY, DEV
}
