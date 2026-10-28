package network.foodsharing.report.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.ReportsApi;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.MediationInfo;
import network.foodsharing.api.model.MediationRequestCreate;
import network.foodsharing.api.model.Report;
import network.foodsharing.api.model.ReportCreate;
import network.foodsharing.api.model.ReportPage;
import network.foodsharing.api.model.ReportStatus;
import network.foodsharing.api.model.ReportSummary;
import network.foodsharing.api.model.ReportUpdate;
import network.foodsharing.api.model.WallEntry;
import network.foodsharing.api.model.WallEntryCreate;
import network.foodsharing.api.model.WallEntryPage;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Contract stub (docs/plan-m6.md B1): every operation answers 501 until implemented. */
@RestController
public class ReportsController implements ReportsApi {

    @Override
    public ResponseEntity<Report> createReport(UUID userId, ReportCreate reportCreate) {
        throw ApiException.notImplemented("createReport");
    }

    @Override
    public ResponseEntity<WallEntry> createReportWallPost(UUID reportId, WallEntryCreate wallEntryCreate) {
        throw ApiException.notImplemented("createReportWallPost");
    }

    @Override
    public ResponseEntity<Void> deleteReport(UUID reportId) {
        throw ApiException.notImplemented("deleteReport");
    }

    @Override
    public ResponseEntity<Void> deleteReportWallPost(UUID reportId, UUID postId) {
        throw ApiException.notImplemented("deleteReportWallPost");
    }

    @Override
    public ResponseEntity<MediationInfo> getMediationInfo(UUID regionId) {
        throw ApiException.notImplemented("getMediationInfo");
    }

    @Override
    public ResponseEntity<Report> getReport(UUID reportId) {
        throw ApiException.notImplemented("getReport");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getReportLog(UUID regionId, LocalDate from, LocalDate to, Integer page, Integer size) {
        throw ApiException.notImplemented("getReportLog");
    }

    @Override
    public ResponseEntity<ReportPage> listRegionReports(UUID regionId, ReportStatus status, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionReports");
    }

    @Override
    public ResponseEntity<WallEntryPage> listReportWall(UUID reportId, Integer page, Integer size) {
        throw ApiException.notImplemented("listReportWall");
    }

    @Override
    public ResponseEntity<List<ReportSummary>> listUserReports(UUID userId) {
        throw ApiException.notImplemented("listUserReports");
    }

    @Override
    public ResponseEntity<Void> requestMediation(UUID regionId, MediationRequestCreate mediationRequestCreate) {
        throw ApiException.notImplemented("requestMediation");
    }

    @Override
    public ResponseEntity<Report> updateReport(UUID reportId, ReportUpdate reportUpdate) {
        throw ApiException.notImplemented("updateReport");
    }
}
