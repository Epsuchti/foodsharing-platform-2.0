package network.foodsharing.fsp.service;

import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.RegionStatisticsContributor;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.user.service.AccountDeletionContributor;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/** SPI implementations of the FSP module (R13 statistics, A40 account data, history and upload access). */
final class FspContributors {

    private FspContributors() {
    }

    /** R13: active FSPs in the region subtree. */
    @Component
    static class Statistics implements RegionStatisticsContributor {

        private final FoodSharePointRepository fsps;

        Statistics(FoodSharePointRepository fsps) {
            this.fsps = fsps;
        }

        @Override
        public void contribute(UUID regionId, Set<UUID> subtreeIds, RegionStatistics statistics) {
            statistics.setActiveFoodSharePoints(subtreeIds.isEmpty() ? 0
                    : (int) fsps.countInRegions(subtreeIds, FspStatus.ACTIVE));
        }
    }

    /** A40: follower/manager rows are removed; wall posts and created FSPs stay with the anonymised account. */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final FspService service;
        private final EntityManager em;

        AccountData(FspService service, EntityManager em) {
            this.service = service;
            this.em = em;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            service.removeUser(userId);
        }

        @Override
        public String section() {
            return "foodSharePoints";
        }

        @Override
        public Object export(UUID userId) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("created", UserRows.read(em, "FoodSharePoint", "createdByUserId", userId, "id", "name",
                    "status", "createdAt"));
            data.put("following", UserRows.read(em, "FspFollower", "userId", userId, "fspId", "role", "bell",
                    "mail", "createdAt"));
            data.put("wallPosts", UserRows.read(em, "FspWallPost", "authorId", userId, "fspId", "body", "createdAt",
                    "deletedAt"));
            return data;
        }
    }

    /** An FSP's history: ORGA and members of its region. */
    @Component
    static class HistoryAccess implements HistoryAccessRule {

        private final FoodSharePointRepository fsps;
        private final PermissionService permissions;

        HistoryAccess(FoodSharePointRepository fsps, PermissionService permissions) {
            this.fsps = fsps;
            this.permissions = permissions;
        }

        @Override
        public SubjectType subjectType() {
            return SubjectType.FSP;
        }

        @Override
        public boolean canRead(SessionPrincipal actor, UUID subjectId) {
            return permissions.isOrga(actor) || fsps.findById(subjectId)
                    .map(f -> permissions.isMemberOf(actor, f.getRegionId())).orElse(false);
        }
    }

    /** FSP photos are public (A41, {@code FSP_PHOTO}). */
    @Component
    static class UploadAccess implements UploadAccessRule {

        @Override
        public String module() {
            return FspService.PHOTO_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return true;
        }
    }
}
