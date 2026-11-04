package network.foodsharing.security;

/**
 * Names of the {@code NavCapability} enum of {@code SessionInfo.capabilities} (decision 161). Each module adds the
 * capabilities whose rule it owns through a {@link SessionInfoEnricher}; plain strings because generated models stay
 * in {@code web}/{@code mapper} (ArchUnit rule 3).
 */
public final class NavCapabilities {

    public static final String QUIZ_EDIT = "QUIZ_EDIT";
    public static final String MANAGE_NEWCOMERS = "MANAGE_NEWCOMERS";
    public static final String VERIFY = "VERIFY";
    public static final String MANAGE_MEMBERS = "MANAGE_MEMBERS";
    public static final String NEWSLETTERS = "NEWSLETTERS";
    public static final String BLOG_MANAGE = "BLOG_MANAGE";
    public static final String CONTENT_EDIT = "CONTENT_EDIT";

    private NavCapabilities() {
    }
}
