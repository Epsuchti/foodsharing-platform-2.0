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
