package network.foodsharing.event.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** Account deletion / export of event invitations (A40); authored events and wall posts stay (anonymised author). */
@Component
public class EventAccountData implements AccountDeletionContributor, DataExportContributor {

    private final EntityManager em;

    public EventAccountData(EntityManager em) {
        this.em = em;
    }

    @Override
    public void onAccountDeleted(UUID userId) {
        UserRows.delete(em, "EventInvitation", "userId", userId);
    }

    @Override
    public String section() {
        return "events";
    }

    @Override
    public Object export(UUID userId) {
        return UserRows.read(em, "EventInvitation", "userId", userId, "eventId", "status", "respondedAt");
    }
}
