package network.foodsharing.intropickup.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * Account deletion / export of intro pickups (A40): open bookings are released, comments about the user, the guide
 * profile, templates and subscriptions are removed; guided intro pickups and authored comments stay (anonymised).
 */
@Component
public class IntroPickupAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public IntroPickupAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        em.createQuery("update IntroPickupSlot s set s.bookingUserId = null, s.bookedAt = null, "
                + "s.status = network.foodsharing.intropickup.domain.SlotStatus.AVAILABLE "
                + "where s.bookingUserId = :userId and s.status = network.foodsharing.intropickup.domain.SlotStatus.BOOKED")
                .setParameter("userId", userId).executeUpdate();
        UserRows.delete(em, "IntroPickupComment", "bookingUserId", userId);
        UserRows.delete(em, "GuideProfile", "userId", userId);
        UserRows.delete(em, "PrivateInfoTemplate", "guideId", userId);
        UserRows.delete(em, "IntroPickupSubscription", "userId", userId);
    }

    @Override
    public String section() {
        return "intropickups";
    }

    @Override
    public Object export(UUID userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookings", UserRows.read(em, "IntroPickupSlot", "bookingUserId", userId, "introPickupId", "status", "bookedAt", "doneAt"));
        data.put("guided", UserRows.read(em, "IntroPickup", "guideId", userId, "regionId", "startAt", "businessName"));
        data.put("comments", UserRows.read(em, "IntroPickupComment", "authorId", userId, "slotId", "text", "createdAt"));
        data.put("guideProfile", UserRows.read(em, "GuideProfile", "userId", userId, "phone", "icalLink"));
        data.put("subscriptions", UserRows.read(em, "IntroPickupSubscription", "userId", userId, "regionId", "channel", "deliveryEmail", "language", "active"));
        return data;
    }
}
