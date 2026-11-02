package network.foodsharing.region.service;

import java.util.UUID;

/** SPI (decision 154): cleaning numbers of a member; implemented by the fsp module. */
public interface MemberCleaningStats {

    int cleaningShiftCount(UUID userId);
}
