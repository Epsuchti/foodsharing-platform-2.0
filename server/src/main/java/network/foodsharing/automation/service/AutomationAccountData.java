package network.foodsharing.automation.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of automation records (A40): cleaning exemptions are removed, policy and advert audits
 * stay without the user; sign-out cancellations stay (anonymised user) for the store log.
 */
@Component
public class AutomationAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public AutomationAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "CleaningRuleExemption", "userId", userId);
        em.createQuery("update PickupPolicyAudit a set a.userId = null where a.userId = :userId")
                .setParameter("userId", userId).executeUpdate();
        em.createQuery("update RequestPolicyAudit a set a.userId = null where a.userId = :userId")
                .setParameter("userId", userId).executeUpdate();
        em.createQuery("update OpenSlotAdvertAudit a set a.userId = null where a.userId = :userId")
                .setParameter("userId", userId).executeUpdate();
    }

    @Override
    public String section() {
        return "automation";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exemptions", UserRows.read(em, "CleaningRuleExemption", "userId", userId, "regionId", "reason"));
        data.put("pickupPolicyDecisions", UserRows.read(em, "PickupPolicyAudit", "userId", userId, "storeId", "pickupAt", "decision", "userMessage"));
        data.put("requestPolicyDecisions", UserRows.read(em, "RequestPolicyAudit", "userId", userId, "storeId", "status", "message"));
        data.put("openSlotAdverts", UserRows.read(em, "OpenSlotAdvertAudit", "userId", userId, "storeId", "pickupAt", "sentAt"));
        data.put("signoutCancellations", UserRows.read(em, "SignoutCancellation", "userId", userId, "storeId", "pickupAt", "cancelledAt"));
        return data;
    }
}
