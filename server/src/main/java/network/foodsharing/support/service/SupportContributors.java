package network.foodsharing.support.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.support.repository.SupportTicketRepository;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** SPI implementations of the support module. */
final class SupportContributors {

    private SupportContributors() {
    }

    /** Attachments: only the uploader and ORGA read them (no rule grant); they go with their ticket. */
    @Component
    static class Attachments implements UploadAccessRule {

        private final SupportTicketRepository tickets;

        Attachments(SupportTicketRepository tickets) {
            this.tickets = tickets;
        }

        @Override
        public String module() {
            return SupportService.ATTACHMENT_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return false;
        }

        @Override
        public boolean exists(UUID usageOwnerId) {
            return tickets.existsById(usageOwnerId);
        }
    }

    /** Tickets are anonymised on account deletion and exported (GDPR, A40). */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final SupportService support;
        private final EntityManager em;

        AccountData(SupportService support, EntityManager em) {
            this.support = support;
            this.em = em;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            support.anonymise(userId);
        }

        @Override
        public String section() {
            return "supportTickets";
        }

        @Override
        public Object export(UUID userId) {
            return UserRows.read(em, "SupportTicket", "userId", userId, "id", "subject", "body", "page", "userAgent",
                    "createdAt");
        }
    }
}
