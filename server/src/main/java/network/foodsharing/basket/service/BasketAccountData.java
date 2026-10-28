package network.foodsharing.basket.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** Account deletion / export of baskets (A40): own baskets become DELETED, own requests are removed. */
@Component
public class BasketAccountData implements AccountDeletionContributor, DataExportContributor {

    private final BasketService baskets;
    private final EntityManager em;

    public BasketAccountData(BasketService baskets, EntityManager em) {
        this.baskets = baskets;
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        baskets.deleteAllOf(userId);
    }

    @Override
    public String section() {
        return "baskets";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baskets", UserRows.read(em, "Basket", "ownerId", userId, "id", "description", "status",
                "locationType", "lat", "lon", "phone", "mobile", "createdAt", "expiresAt", "takenAt"));
        data.put("requests", UserRows.read(em, "BasketRequestEntry", "requesterId", userId, "basketId", "status",
                "message", "requestedAt", "answeredAt"));
        return data;
    }
}
