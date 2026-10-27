package network.foodsharing.history;

/** How long entries of an action are kept ({@code HistoryRetentionJob}). */
public enum RetentionPolicy {
    /** Never deleted. */
    FOREVER,
    YEARS_2,
    DAYS_90,
    /** Kept while the account exists; anonymised on account deletion (milestone 6). */
    ACCOUNT_LIFETIME
}
