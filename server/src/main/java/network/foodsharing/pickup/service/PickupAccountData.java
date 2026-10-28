package network.foodsharing.pickup.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of pickups (A40): future sign-ups are removed through
 * {@link PickupUserService#deleteAllFuture} (store history entries per store); past sign-ups stay for the statistics.
 */
@Component
public class PickupAccountData implements AccountDeletionContributor, DataExportContributor {

    static final String REASON = "Konto gelöscht";

    private final PickupUserService pickups;
    private final EntityManager em;

    public PickupAccountData(PickupUserService pickups, EntityManager em) {
        this.pickups = pickups;
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        pickups.deleteAllFuture(userId, REASON);
    }

    @Override
    public String section() {
        return "pickups";
    }

    @Override
    public Object export(UUID userId) {
        return UserRows.read(em, "PickupSignup", "userId", userId, "storeId", "pickupAt", "confirmed", "createdAt");
    }
}
