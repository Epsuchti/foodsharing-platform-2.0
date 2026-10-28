package network.foodsharing.user.service;

import java.util.UUID;

/**
 * A module removes or anonymises its user-related rows when an account is deleted (A40); runs inside the deletion
 * transaction. Default: no beans.
 */
public interface AccountDeletionContributor {

    void onAccountDeleted(UUID userId);
}
