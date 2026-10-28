package network.foodsharing.region.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Mailbox address of a region or working group (e.g. the mediation team, G2); implemented by the mailbox module so
 * that modules depending on {@code region} need no mailbox dependency (pattern A22). No bean = no address.
 */
public interface GroupMailboxAddress {

    Optional<String> of(UUID regionOrGroupId);
}
