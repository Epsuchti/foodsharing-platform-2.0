package network.foodsharing.region.service;

import java.util.UUID;

/**
 * Figures for working-group application requirements (plan §13.2 R9: min pickups, min bananas). Implemented by the
 * pickup module (M3) and the banana feature (M6); without implementations both counts are 0, so a requirement above 0
 * is not met (shown in {@code requirementCheck}).
 */
public interface GroupRequirementContributor {

    /** Number of pickups of the user (0 when unknown to this contributor). */
    default int pickups(UUID userId) {
        return 0;
    }

    /** Number of bananas of the user (0 when unknown to this contributor). */
    default int bananas(UUID userId) {
        return 0;
    }
}
