package network.foodsharing.report.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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

    /** Option set 1 of {@code REPORT_REASON_OPTIONS}: the pickup reasons. */
    public static final List<ReportReason> PICKUP_REASONS =
            List.of(ReportReason.LATE, ReportReason.NO_SHOW, ReportReason.CANCELLATION, ReportReason.SELLS);

    /** Option set 2 of {@code REPORT_REASON_OPTIONS}: the rule-book violations. */
    public static final List<ReportReason> RULE_BOOK_REASONS = Arrays.stream(ReportReason.values())
            .filter(r -> r.name().startsWith("VIOLATED_")).toList();

    /**
     * Selectable reasons (without OTHER) of a region's {@code REPORT_REASON_OPTIONS} value: 1 = pickup reasons,
     * 2 = rule-book violations, 3 = both; any other value falls back to the default set 1.
     */
    public static List<ReportReason> reasonOptions(int optionSet) {
        return switch (optionSet) {
            case 2 -> RULE_BOOK_REASONS;
            case 3 -> Stream.concat(PICKUP_REASONS.stream(), RULE_BOOK_REASONS.stream()).toList();
            default -> PICKUP_REASONS;
        };
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
