package network.foodsharing.region.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** Account deletion / export of the region module (A40): memberships, admin roles, permissions, applications. */
@Component
public class RegionAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public RegionAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "RegionMembership", "userId", userId);
        UserRows.delete(em, "RegionAdmin", "userId", userId);
        UserRows.delete(em, "RegionPermission", "userId", userId);
        UserRows.delete(em, "GroupApplication", "userId", userId);
    }

    @Override
    public String section() {
        return "regions";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberships", UserRows.read(em, "RegionMembership", "userId", userId, "regionId", "joinedAt",
                "applicationText"));
        result.put("adminOf", UserRows.read(em, "RegionAdmin", "userId", userId, "regionId", "createdAt"));
        result.put("groupApplications", UserRows.read(em, "GroupApplication", "userId", userId, "groupId", "text",
                "createdAt", "decidedAt", "decisionMessage"));
        return result;
    }
}
