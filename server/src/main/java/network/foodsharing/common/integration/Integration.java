package network.foodsharing.common.integration;

/** External systems guarded by a circuit breaker (docs/conventions.md A26). IMAP is declared for milestone 5. */
public enum Integration {
    SMTP, WEB_PUSH, TELEGRAM, GEOCODING, HOLIDAY_CALENDAR, WALLET_APPLE, WALLET_GOOGLE, IMAP
}
