package network.foodsharing.admin.domain;

/** Known feature toggles (A43); a missing row means {@link #defaultEnabled()}. */
public enum FeatureToggle {
    BASKETS(true),
    FOOD_SHARE_POINTS(true),
    MAP_USERS(true),
    RESOURCES(true),
    BLOG(true),
    DONATION_MODAL(false),
    PETITION_BANNER(false),
    OAUTH_PROVIDER(true),
    SUPPORT_FORM(true),
    GLOBAL_SEARCH(true);

    private final boolean defaultEnabled;

    FeatureToggle(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean defaultEnabled() {
        return defaultEnabled;
    }
}
