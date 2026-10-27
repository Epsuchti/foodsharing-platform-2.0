package network.foodsharing.common.seed;

import java.time.Duration;

/**
 * Moves a module's seed timestamps forward so the local seed stays "current" (docs/conventions.md A13). Called once
 * per start of the {@code local} profile by {@code dev.SeedDateShiftRunner} with the delta between the last anchor
 * and today (whole days). Implementations shift only seed rows (fixed seed ids) with plain SQL updates.
 */
public interface SeedDateShiftContributor {

    void shift(Duration delta);
}
