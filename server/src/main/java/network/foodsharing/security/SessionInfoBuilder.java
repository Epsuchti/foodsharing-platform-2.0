package network.foodsharing.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Collects what {@link SessionInfoEnricher}s contribute to the session info. Plain values only (no generated models:
 * ArchUnit rule 3); {@code auth.mapper.AuthMapper} turns it into the generated {@code SessionInfo}.
 */
public final class SessionInfoBuilder {

    /** A region or working group of the user; {@code type}/{@code function} are enum names. */
    public record Unit(UUID id, String name, String slug, String type, UUID parentId, boolean member, boolean admin,
                       boolean homeRegion, String function) {
    }

    private UUID homeRegionId;
    private final List<Unit> units = new ArrayList<>();
    private final Set<String> pendingConsents = new LinkedHashSet<>();
    private final Set<String> extraPermissions = new LinkedHashSet<>();
    private final Set<String> grantedPermissions = new LinkedHashSet<>();
    private Boolean onboardingComplete;
    private String onboardingCurrentStep;
    private Boolean mailboxes;
    private final Set<String> capabilities = new LinkedHashSet<>();

    public SessionInfoBuilder homeRegionId(UUID regionId) {
        this.homeRegionId = regionId;
        return this;
    }

    public SessionInfoBuilder addUnit(Unit unit) {
        units.add(unit);
        return this;
    }

    public SessionInfoBuilder addPendingConsent(String legalDocumentKind) {
        pendingConsents.add(legalDocumentKind);
        return this;
    }

    /** The permissions the session already carries ({@code <PERMISSION>@<regionId>}), so enrichers need no query. */
    public SessionInfoBuilder grantedPermissions(java.util.Collection<String> permissions) {
        grantedPermissions.addAll(permissions);
        return this;
    }

    /** Holds the permission for some region ({@code <P>@<regionId>} or {@code <P>@*}), granted or added. */
    public boolean holdsPermissionAnywhere(String permission) {
        String prefix = permission + "@";
        return grantedPermissions.stream().anyMatch(p -> p.startsWith(prefix))
                || extraPermissions.stream().anyMatch(p -> p.startsWith(prefix));
    }

    public SessionInfoBuilder addPermission(String permission) {
        extraPermissions.add(permission);
        return this;
    }

    public SessionInfoBuilder onboarding(boolean complete, String currentStep) {
        this.onboardingComplete = complete;
        this.onboardingCurrentStep = currentStep;
        return this;
    }

    public SessionInfoBuilder mailboxes(boolean mailboxes) {
        this.mailboxes = mailboxes;
        return this;
    }

    /** A menu capability ({@code NavCapability} enum name, decision 159); added by the module that owns the rule. */
    public SessionInfoBuilder capability(String capability) {
        capabilities.add(capability);
        return this;
    }

    public UUID homeRegionId() {
        return homeRegionId;
    }

    public List<Unit> units() {
        return List.copyOf(units);
    }

    /**
     * Admin of at least one region or working group, from the units the region enricher ({@code @Order(10)}) added.
     * Later enrichers use it to skip their admin queries for everybody else (QueryCountIT session budget).
     */
    public boolean adminOfAnyUnit() {
        return units.stream().anyMatch(Unit::admin);
    }

    public List<String> pendingConsents() {
        return List.copyOf(pendingConsents);
    }

    public List<String> extraPermissions() {
        return List.copyOf(extraPermissions);
    }

    public Boolean onboardingComplete() {
        return onboardingComplete;
    }

    public String onboardingCurrentStep() {
        return onboardingCurrentStep;
    }

    public Boolean mailboxes() {
        return mailboxes;
    }

    public List<String> capabilities() {
        return List.copyOf(capabilities);
    }
}
