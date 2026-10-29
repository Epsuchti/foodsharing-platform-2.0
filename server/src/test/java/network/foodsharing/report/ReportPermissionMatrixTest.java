package network.foodsharing.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.report.domain.ReportReason;
import network.foodsharing.report.service.ReportRules;
import network.foodsharing.report.service.ReportRules.ReporterFacts;
import org.junit.jupiter.api.Test;

/** G1/G2 permission matrix (upstream ReportPermissions). */
class ReportPermissionMatrixTest {

    @Test
    void filingNeedsAnotherMemberVerificationAndTheReportButton() {
        assertThat(ReportRules.reportDenial(new ReporterFacts(false, true, true))).isNull();
        assertThat(ReportRules.reportDenial(new ReporterFacts(true, true, true))).isEqualTo(ApiErrorCode.REPORT_SELF);
        assertThat(ReportRules.reportDenial(new ReporterFacts(false, false, true)))
                .isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(ReportRules.reportDenial(new ReporterFacts(false, true, false)))
                .isEqualTo(ApiErrorCode.REPORT_BUTTON_DISABLED);
    }

    @Test
    void reportRegionIsTheStoresRegionElseTheHomeRegion() {
        UUID store = UUID.randomUUID();
        UUID home = UUID.randomUUID();
        assertThat(ReportRules.reportRegion(store, home)).isEqualTo(store);
        assertThat(ReportRules.reportRegion(null, home)).isEqualTo(home);
        assertThat(ReportRules.reportRegion(null, null)).isNull();
    }

    @Test
    void reasonOptionSets() {
        assertThat(ReportRules.reasonOptions(1)).containsExactly(ReportReason.LATE, ReportReason.NO_SHOW,
                ReportReason.CANCELLATION, ReportReason.SELLS);
        assertThat(ReportRules.reasonOptions(2)).hasSize(20).allMatch(r -> r.name().startsWith("VIOLATED_"))
                .startsWith(ReportReason.VIOLATED_B1_0).endsWith(ReportReason.VIOLATED_B13);
        assertThat(ReportRules.reasonOptions(3)).hasSize(24).startsWith(ReportReason.LATE)
                .endsWith(ReportReason.VIOLATED_B13);
        assertThat(ReportRules.reasonOptions(0)).isEqualTo(ReportRules.reasonOptions(1));
        assertThat(ReportRules.reasonOptions(7)).isEqualTo(ReportRules.reasonOptions(1));
        for (int set = 0; set <= 3; set++) {
            assertThat(ReportRules.reasonOptions(set)).doesNotContain(ReportReason.OTHER);
        }
    }

    @Test
    void filingAllowsOnlyTheRegionsReasons() {
        assertThat(ReportRules.reasonAllowed(ReportReason.LATE, 1, false)).isTrue();
        assertThat(ReportRules.reasonAllowed(ReportReason.VIOLATED_B2, 1, false)).isFalse();
        assertThat(ReportRules.reasonAllowed(ReportReason.VIOLATED_B2, 2, false)).isTrue();
        assertThat(ReportRules.reasonAllowed(ReportReason.LATE, 2, false)).isFalse();
        assertThat(ReportRules.reasonAllowed(ReportReason.LATE, 3, false)).isTrue();
        assertThat(ReportRules.reasonAllowed(ReportReason.VIOLATED_B13, 3, false)).isTrue();
        assertThat(ReportRules.reasonAllowed(ReportReason.SELLS, 9, false)).isTrue(); // unknown set = default 1
        assertThat(ReportRules.reasonAllowed(ReportReason.OTHER, 3, false)).isFalse();
        assertThat(ReportRules.reasonAllowed(ReportReason.OTHER, 1, true)).isTrue();
        assertThat(ReportRules.reasonAllowed(null, 3, true)).isFalse();
        for (ReportReason reason : ReportReason.values()) {
            for (int set = 1; set <= 3; set++) {
                for (boolean other : new boolean[] {false, true}) {
                    assertThat(ReportRules.reasonAllowed(reason, set, other)).as(reason + "/" + set + "/" + other)
                            .isEqualTo(reason == ReportReason.OTHER ? other
                                    : ReportRules.reasonOptions(set).contains(reason));
                }
            }
        }
    }

    @Test
    void otherNeedsAReasonText() {
        assertThat(ReportRules.reasonTextValid(ReportReason.OTHER, " ")).isFalse();
        assertThat(ReportRules.reasonTextValid(ReportReason.OTHER, null)).isFalse();
        assertThat(ReportRules.reasonTextValid(ReportReason.OTHER, "Ton")).isTrue();
        assertThat(ReportRules.reasonTextValid(ReportReason.LATE, null)).isTrue();
    }

    @Test
    void handlingDeletingAndWallPosts() {
        assertThat(ReportRules.mayHandle(true, false)).isTrue();
        assertThat(ReportRules.mayHandle(false, true)).isTrue();
        assertThat(ReportRules.mayHandle(false, false)).isFalse();
        assertThat(ReportRules.mayDelete(true)).isTrue();
        assertThat(ReportRules.mayDelete(false)).isFalse();
        assertThat(ReportRules.mayDeleteWallPost(false, true)).isTrue();
        assertThat(ReportRules.mayDeleteWallPost(true, false)).isTrue();
        assertThat(ReportRules.mayDeleteWallPost(false, false)).isFalse();
    }

    @Test
    void mediationNeedsMembershipAndTheOption() {
        assertThat(ReportRules.mediationDenial(true, true, false)).isNull();
        assertThat(ReportRules.mediationDenial(false, true, false)).isEqualTo(ApiErrorCode.MEDIATION_DISABLED);
        assertThat(ReportRules.mediationDenial(true, false, false)).isEqualTo(ApiErrorCode.ACCESS_DENIED);
        assertThat(ReportRules.mediationDenial(true, false, true)).isNull();
        assertThat(ReportRules.mediationDenial(false, false, true)).isEqualTo(ApiErrorCode.MEDIATION_DISABLED);
    }
}
