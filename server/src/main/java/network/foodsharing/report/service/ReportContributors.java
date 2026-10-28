package network.foodsharing.report.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.report.repository.MediationRequestRepository;
import network.foodsharing.report.repository.ReportRepository;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** SPI implementations of the report module (A40 account data, history access of the report log). */
final class ReportContributors {

    private ReportContributors() {
    }

    /**
     * A40: reports by and about a deleted member, their wall posts and mediation requests stay (governance record)
     * and point at the anonymised account row; the export lists the reports the member filed and their requests.
     * Reports about the member are not exported (they belong to the report team's process).
     */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final EntityManager em;

        AccountData(EntityManager em) {
            this.em = em;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            // nothing to remove: authored reports, wall posts and mediation requests stay with the anonymised row
        }

        @Override
        public String section() {
            return "reports";
        }

        @Override
        public Object export(UUID userId) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("filed", UserRows.read(em, "Report", "reporterId", userId, "id", "reason", "reasonText",
                    "message", "status", "createdAt"));
            data.put("wallPosts", UserRows.read(em, "ReportWallPost", "authorId", userId, "reportId", "body",
                    "createdAt", "deletedAt"));
            data.put("mediationRequests", UserRows.read(em, "MediationRequest", "requesterId", userId, "regionId",
                    "message", "createdAt"));
            return data;
        }
    }

    /** REPORT history (reports and mediation requests): the report handlers of the region, ORGA. */
    @Component
    static class HistoryAccess implements HistoryAccessRule {

        private final ReportRepository reports;
        private final MediationRequestRepository requests;
        private final ReportPermissionService access;

        HistoryAccess(ReportRepository reports, MediationRequestRepository requests, ReportPermissionService access) {
            this.reports = reports;
            this.requests = requests;
            this.access = access;
        }

        @Override
        public SubjectType subjectType() {
            return SubjectType.REPORT;
        }

        @Override
        public boolean canRead(SessionPrincipal actor, UUID subjectId) {
            if (access.isOrga(actor)) {
                return true;
            }
            UUID regionId = reports.findById(subjectId).map(r -> r.getRegionId())
                    .or(() -> requests.findById(subjectId).map(r -> r.getRegionId())).orElse(null);
            return regionId != null && access.mayHandle(actor, regionId);
        }
    }
}
