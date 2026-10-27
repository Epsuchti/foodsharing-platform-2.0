package network.foodsharing.security;

/**
 * Working-group functions with region-wide rights (plan §9, upstream {@code WorkgroupFunction}). A region assigns at
 * most one working group per function ({@code region_function}). Lives in {@code security} (not {@code region}) so
 * {@link ScopedPermissionResolver} and {@link PermissionService} can name it without depending on a feature module.
 */
public enum WorkingGroupFunction {
    WELCOME,
    VOTING,
    FSP,
    STORES_COORDINATION,
    STORE_CHAINS,
    REPORT,
    MEDIATION,
    ARBITRATION,
    FSMANAGEMENT,
    PR,
    MODERATION,
    BOARD,
    ELECTION,
    RESOURCES;

    /** Only ORGA may assign these functions (upstream {@code isRestrictedWorkgroupFunction}). */
    public boolean isRestricted() {
        return this == REPORT || this == ARBITRATION || this == FSMANAGEMENT || this == BOARD;
    }
}
