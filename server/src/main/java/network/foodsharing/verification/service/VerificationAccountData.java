package network.foodsharing.verification.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of passports (A40): passport issues, requests and wallet passes are removed.
 */
@Component
public class VerificationAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public VerificationAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "WalletPass", "userId", userId);
        UserRows.delete(em, "PassportRequest", "userId", userId);
        UserRows.delete(em, "PassportIssue", "userId", userId);
    }

    @Override
    public String section() {
        return "verification";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("passports", UserRows.read(em, "PassportIssue", "userId", userId, "kind", "validFrom", "validUntil", "regionId"));
        data.put("passportRequests", UserRows.read(em, "PassportRequest", "userId", userId, "requestedAt", "fulfilledAt"));
        data.put("walletPasses", UserRows.read(em, "WalletPass", "userId", userId, "walletType", "serial"));
        return data;
    }
}
