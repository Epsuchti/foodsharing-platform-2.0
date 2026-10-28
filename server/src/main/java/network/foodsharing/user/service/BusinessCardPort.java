package network.foodsharing.user.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Business card PDF of the current user (A14, upstream {@code BusinessCardControl}); implemented by the
 * {@code profile} module. Also hosts the personal mailbox address lookup that the {@code mailbox} module provides.
 */
public interface BusinessCardPort {

    /**
     * @param role FOODSAVER, STORE_MANAGER or AMBASSADOR (at most the own role)
     * @throws network.foodsharing.common.error.ApiException 403 BUSINESS_CARD_NOT_ALLOWED, 400 NOT_REGION_MEMBER
     */
    byte[] render(String role, UUID regionId);

    /** The address of a user's personal mailbox, when they have one (implemented by {@code mailbox}). */
    interface PersonalMailboxAddress {
        Optional<String> of(UUID userId);
    }
}
