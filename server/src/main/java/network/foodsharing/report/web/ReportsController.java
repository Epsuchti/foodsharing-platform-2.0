package network.foodsharing.report.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.report.domain.ReportReason;
import network.foodsharing.report.mapper.ReportMapper;
import network.foodsharing.report.service.MediationService;
import network.foodsharing.report.service.ReportLogService;
import network.foodsharing.report.service.ReportService;
import network.foodsharing.report.service.ReportService.PostView;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Reports, report wall, report log and mediation (G1, G2, D10.3). */
@RestController
public class ReportsController implements ReportsApi {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final int DETAIL_WALL_SIZE = 50;

    private final ReportService reports;
    private final MediationService mediation;
    private final ReportLogService log;
    private final ReportMapper mapper;
    private final HistoryMapper historyMapper;

    public ReportsController(ReportService reports, MediationService mediation, ReportLogService log,
                             ReportMapper mapper, HistoryMapper historyMapper) {
        this.reports = reports;
        this.mediation = mediation;
        this.log = log;
        this.mapper = mapper;
        this.historyMapper = historyMapper;
    }

    @Override
    public ResponseEntity<Report> createReport(UUID userId, ReportCreate body) {
        var saved = reports.create(userId, new ReportService.ReportInput(
                body.getReason() == null ? null : ReportReason.valueOf(body.getReason().name()), body.getReasonText(),
                body.getMessage(), body.getStoreId(), Boolean.TRUE.equals(body.getSendConfirmationMail())));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toReport(
                new ReportService.ReportView(saved, reports.store(saved.getStoreId()).orElse(null), false, false,
                        false), List.of()));
    }

    @Override
    public ResponseEntity<WallEntry> createReportWallPost(UUID reportId, WallEntryCreate body) {
        var post = reports.post(reportId, body.getBody());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toWallEntries(List.of(new PostView(post, true))).getFirst());
    }

    @Override
    public ResponseEntity<Void> deleteReport(UUID reportId) {
        reports.delete(reportId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteReportWallPost(UUID reportId, UUID postId) {
        reports.deletePost(reportId, postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<MediationInfo> getMediationInfo(UUID regionId) {
        return ResponseEntity.ok(mapper.toMediationInfo(mediation.info(regionId)));
    }

    @Override
    public ResponseEntity<Report> getReport(UUID reportId) {
        return ResponseEntity.ok(mapper.toReport(reports.get(reportId),
                reports.latestWall(reportId, DETAIL_WALL_SIZE)));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getReportLog(UUID regionId, LocalDate from, LocalDate to, Integer page,
                                                         Integer size) {
        Instant start = from == null ? null : from.atStartOfDay(ZURICH).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ZURICH).toInstant();
        return ResponseEntity.ok(historyMapper.toPage(log.log(regionId, start, end, page == null ? 0 : page,
                size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<ReportPage> listRegionReports(UUID regionId, ReportStatus status, Integer page,
                                                        Integer size) {
        Page<network.foodsharing.report.domain.Report> result = reports.list(regionId, status == null ? null
                : network.foodsharing.report.domain.ReportStatus.valueOf(status.name()), page, size);
        return ResponseEntity.ok(new ReportPage().items(mapper.toSummaries(result.getContent()))
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<WallEntryPage> listReportWall(UUID reportId, Integer page, Integer size) {
        Page<PostView> result = reports.wall(reportId, page, size);
        return ResponseEntity.ok(new WallEntryPage().items(mapper.toWallEntries(result.getContent()))
                .page(PageMapper.toPageMeta(result)));
    }

    @Override
    public ResponseEntity<List<ReportSummary>> listUserReports(UUID userId) {
        return ResponseEntity.ok(mapper.toSummaries(reports.listForUser(userId)));
    }

    @Override
    public ResponseEntity<Void> requestMediation(UUID regionId, MediationRequestCreate body) {
        mediation.request(regionId, body.getMessage(), body.getCounterpartUserId());
        return ResponseEntity.accepted().build();
    }

    @Override
    public ResponseEntity<Report> updateReport(UUID reportId, ReportUpdate body) {
        var view = reports.update(reportId, new ReportService.ReportChange(
                body.getStatus() == null ? null
                        : network.foodsharing.report.domain.ReportStatus.valueOf(body.getStatus().name()),
                body.getConsequence(), TimeMapper.toInstant(body.getReminderAt()), body.getForumThreadId()));
        return ResponseEntity.ok(mapper.toReport(view, reports.latestWall(reportId, DETAIL_WALL_SIZE)));
    }
}
