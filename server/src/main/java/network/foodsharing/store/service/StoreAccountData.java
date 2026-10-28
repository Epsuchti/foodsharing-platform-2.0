package network.foodsharing.store.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** Account deletion / export of store team memberships (A40); wall posts stay attributed to the anonymised row. */
@Component
public class StoreAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public StoreAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "StoreMembership", "userId", userId);
    }

    @Override
    public String section() {
        return "stores";
    }

    @Override
    public Object export(UUID userId) {
        return UserRows.read(em, "StoreMembership", "userId", userId, "storeId", "status", "manager", "addedAt",
                "applicationText");
    }
}
