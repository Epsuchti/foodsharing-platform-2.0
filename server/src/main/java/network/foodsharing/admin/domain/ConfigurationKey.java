package network.foodsharing.admin.domain;

/** Runtime configuration keys with defaults (A43); stored in table {@code configuration}. */
public enum ConfigurationKey {
    SUPPORT_EMAIL("support.email", "support@foodsharing.network"),
    PETITION_URL("petition.url", ""),
    PETITION_SIGNATURES("petition.signatures", "0"),
    PETITION_DAYS_LEFT("petition.days-left", "0"),
    DONATION_SHOW_CAMPAIGN_CARD("donation.show-campaign-card", "false"),
    DONATION_SHOW_MODAL("donation.show-modal", "false"),
    DONATION_MODAL_HOURS_LOGGED_IN("donation.modal-hours-logged-in", "168"),
    DONATION_MODAL_HOURS_LOGGED_OUT("donation.modal-hours-logged-out", "24"),
    DONATION_INFO_URL("donation.info-url", ""),
    DONATION_IFRAME_ONE_TIME_URL("donation.iframe-one-time-url", ""),
    DONATION_IFRAME_CAMPAIGN_URL("donation.iframe-campaign-url", ""),
    DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL("donation.iframe-friendship-circle-url", ""),
    DONATION_IFRAME_SELF_SERVICE_URL("donation.iframe-self-service-url", ""),
    STATISTICS_PUBLIC_CACHE_MINUTES("statistics.public-cache-minutes", "60"),
    OAUTH_ISSUER("oauth.issuer", "");

    private final String key;
    private final String defaultValue;

    ConfigurationKey(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }
}
