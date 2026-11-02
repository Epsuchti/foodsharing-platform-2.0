package network.foodsharing.fsp.service;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.FspCleaningSignupRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.region.domain.RegionStatistics;
import network.foodsharing.region.service.MemberCleaningStats;
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

    /** Decision 154: a member's confirmed past cleaning shifts, shown on the profile. */
    @Component
    static class MemberStats implements MemberCleaningStats {

        private final FspCleaningSignupRepository cleaningSignups;
        private final Clock clock;

        MemberStats(FspCleaningSignupRepository cleaningSignups, Clock clock) {
            this.cleaningSignups = cleaningSignups;
            this.clock = clock;
        }

        @Override
        public int cleaningShiftCount(UUID userId) {
            return (int) cleaningSignups.countByUserIdAndConfirmedTrueAndShiftAtLessThan(userId, clock.instant());
        }
    }

    /** R13: active FSPs and last month's confirmed cleaning shifts in the region subtree. */
    @Component
    static class Statistics implements RegionStatisticsContributor {

        private final FoodSharePointRepository fsps;
        private final FspCleaningSignupRepository cleaningSignups;
        private final Clock clock;

        Statistics(FoodSharePointRepository fsps, FspCleaningSignupRepository cleaningSignups, Clock clock) {
            this.fsps = fsps;
            this.cleaningSignups = cleaningSignups;
            this.clock = clock;
        }

        @Override
        public void contribute(UUID regionId, Set<UUID> subtreeIds, RegionStatistics statistics) {
            statistics.setActiveFoodSharePoints(subtreeIds.isEmpty() ? 0
                    : (int) fsps.countInRegions(subtreeIds, FspStatus.ACTIVE));
            ZoneId zone = ZoneId.of("Europe/Zurich");
            ZonedDateTime monthStart = clock.instant().atZone(zone).withDayOfMonth(1).toLocalDate().atStartOfDay(zone);
            statistics.setCleaningShiftsLastMonth(subtreeIds.isEmpty() ? 0
                    : (int) cleaningSignups.countConfirmedInRegions(subtreeIds, monthStart.minusMonths(1).toInstant(),
                    monthStart.toInstant()));
        }
    }

    /**
     * A40: follower/manager rows and future cleaning sign-ups are removed; wall posts, created FSPs and past cleaning
     * sign-ups stay with the anonymised account (decision 155).
     */
    @Component
    static class AccountData implements AccountDeletionContributor, DataExportContributor {

        private final FspService service;
        private final FspCleaningSignupService cleaning;
        private final EntityManager em;

        AccountData(FspService service, FspCleaningSignupService cleaning, EntityManager em) {
            this.service = service;
            this.cleaning = cleaning;
            this.em = em;
        }

        @Override
        public void onAccountDeleted(UUID userId) {
            service.removeUser(userId);
            cleaning.removeFutureSignups(userId);
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
            data.put("cleaningSignups", UserRows.read(em, "FspCleaningSignup", "userId", userId, "fspId", "shiftAt",
                    "confirmed", "confirmedAt", "createdAt"));
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

        private final FoodSharePointRepository fsps;

        UploadAccess(FoodSharePointRepository fsps) {
            this.fsps = fsps;
        }

        @Override
        public String module() {
            return FspService.PHOTO_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return true;
        }

        /** A deleted FSP (row gone or soft deleted) no longer needs its photo. */
        @Override
        public boolean exists(UUID usageOwnerId) {
            return fsps.findById(usageOwnerId).filter(fsp -> fsp.getDeletedAt() == null).isPresent();
        }
    }
}
