package network.foodsharing.admin.domain;

import java.util.Arrays;
import java.util.Optional;

/** Runtime configuration keys with type and default (A43); stored in table {@code configuration}. */
public enum ConfigurationKey {
    SUPPORT_EMAIL("support.email", Type.EMAIL, "support@foodsharing.network"),
    PETITION_URL("petition.url", Type.URL, ""),
    PETITION_SIGNATURES("petition.signatures", Type.INTEGER, "0"),
    PETITION_DAYS_LEFT("petition.days-left", Type.INTEGER, "0"),
    DONATION_SHOW_CAMPAIGN_CARD("donation.show-campaign-card", Type.BOOLEAN, "false"),
    DONATION_SHOW_MODAL("donation.show-modal", Type.BOOLEAN, "false"),
    DONATION_MODAL_HOURS_LOGGED_IN("donation.modal-hours-logged-in", Type.INTEGER, "168"),
    DONATION_MODAL_HOURS_LOGGED_OUT("donation.modal-hours-logged-out", Type.INTEGER, "24"),
    DONATION_INFO_URL("donation.info-url", Type.URL, ""),
    DONATION_IFRAME_ONE_TIME_URL("donation.iframe-one-time-url", Type.URL, ""),
    DONATION_IFRAME_CAMPAIGN_URL("donation.iframe-campaign-url", Type.URL, ""),
    DONATION_IFRAME_FRIENDSHIP_CIRCLE_URL("donation.iframe-friendship-circle-url", Type.URL, ""),
    DONATION_IFRAME_SELF_SERVICE_URL("donation.iframe-self-service-url", Type.URL, ""),
    DONATION_CAMPAIGN_PAGE_SLUGS("donation.campaign-page-slugs", Type.LIST, ""),
    STATISTICS_PUBLIC_CACHE_MINUTES("statistics.public-cache-minutes", Type.INTEGER, "60"),
    OAUTH_ISSUER("oauth.issuer", Type.URL, "");

    /** Value types; empty values are allowed for URL and LIST (= not set). */
    public enum Type {
        STRING, INTEGER, BOOLEAN, URL, EMAIL, LIST
    }

    private final String key;
    private final Type type;
    private final String defaultValue;

    ConfigurationKey(String key, Type type, String defaultValue) {
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public Type type() {
        return type;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public static Optional<ConfigurationKey> fromKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
