package network.foodsharing.auth.domain;

public enum SessionRevokeReason {
    LOGOUT, REVOKED_BY_USER, LOGOUT_EVERYWHERE, ACCOUNT_DISABLED, EMAIL_CHANGED, ACCOUNT_DELETED
}
