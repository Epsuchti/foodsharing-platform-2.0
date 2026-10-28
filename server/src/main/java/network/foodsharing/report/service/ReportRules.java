package network.foodsharing.report.service;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.report.domain.ReportReason;

/** Pure report rules (G1, upstream {@code ReportPermissions}); matrix in {@code ReportPermissionMatrixTest}. */
public final class ReportRules {

    private ReportRules() {
    }

    /** Facts about a reporter and the reported member. */
    public record ReporterFacts(boolean self, boolean verified, boolean reportButtonInCommonRegion) {
    }

    /** Why a report may not be filed, or {@code null} when it may. */
    public static ApiErrorCode reportDenial(ReporterFacts facts) {
        if (facts.self()) {
            return ApiErrorCode.REPORT_SELF;
        }
        if (!facts.verified()) {
            return ApiErrorCode.ACCESS_DENIED;
        }
        if (!facts.reportButtonInCommonRegion()) {
            return ApiErrorCode.REPORT_BUTTON_DISABLED;
        }
        return null;
    }

    /** The region of a report: the store's region when a store is given, else the reported member's home region. */
    public static UUID reportRegion(UUID storeRegionId, UUID reportedHomeRegionId) {
        return storeRegionId != null ? storeRegionId : reportedHomeRegionId;
    }

    /** OTHER needs a reason text; every other reason ignores it. */
    public static boolean reasonTextValid(ReportReason reason, String reasonText) {
        return reason != ReportReason.OTHER || (reasonText != null && !reasonText.isBlank());
    }

    /** Reports are handled (read, edited, wall) by ORGA and the report handlers of the region (or an ancestor). */
    public static boolean mayHandle(boolean orga, boolean handler) {
        return orga || handler;
    }

    /** Deleting a report is ORGA only (upstream). */
    public static boolean mayDelete(boolean orga) {
        return orga;
    }

    /** A wall post is deleted by ORGA or its author. */
    public static boolean mayDeleteWallPost(boolean orga, boolean author) {
        return orga || author;
    }

    /** Mediation: the region option must be on and the requester a member of the region (ORGA always). */
    public static ApiErrorCode mediationDenial(boolean enabled, boolean member, boolean orga) {
        if (!member && !orga) {
            return ApiErrorCode.ACCESS_DENIED;
        }
        return enabled ? null : ApiErrorCode.MEDIATION_DISABLED;
    }
}
