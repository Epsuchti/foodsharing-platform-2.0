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
    private Boolean onboardingComplete;
    private String onboardingCurrentStep;

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

    public SessionInfoBuilder addPermission(String permission) {
        extraPermissions.add(permission);
        return this;
    }

    public SessionInfoBuilder onboarding(boolean complete, String currentStep) {
        this.onboardingComplete = complete;
        this.onboardingCurrentStep = currentStep;
        return this;
    }

    public UUID homeRegionId() {
        return homeRegionId;
    }

    public List<Unit> units() {
        return List.copyOf(units);
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
}
