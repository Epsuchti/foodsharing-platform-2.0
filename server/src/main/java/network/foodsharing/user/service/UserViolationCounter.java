package network.foodsharing.user.service;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Profile violation count (A6 {@code PublicProfile.violationCount}), implemented by the report module so the profile
 * module (a leaf) does not depend on it. Without an implementation nobody sees a count.
 */
public interface UserViolationCounter {

    /** Reports about {@code userId} the current actor handles, or empty when the actor may not see them. */
    OptionalInt visibleReportCount(UUID userId);
}
