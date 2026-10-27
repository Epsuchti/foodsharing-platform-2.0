package network.foodsharing.verification.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.PassportsApi;
import network.foodsharing.api.model.CreateRegionPassports;
import network.foodsharing.api.model.GoogleWalletLink;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.JobRef;
import network.foodsharing.api.model.PassportRequestView;
import network.foodsharing.api.model.PassportStatus;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.mapper.HistoryMapper;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.verification.mapper.VerificationMapper;
import network.foodsharing.verification.service.PassportPdfRenderer;
import network.foodsharing.verification.service.PassportService;
import network.foodsharing.verification.service.WalletService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Passports and wallet passes (plan §13.9 Ve3). */
@RestController
public class PassportsController implements PassportsApi {

    static final List<String> PASSPORT_ACTIONS = List.of("PASSPORT_GENERATED", "PASSPORT_GENERATED_BY_AMBASSADOR",
            "PASSPORT_REQUESTED", "WALLET_PASS_CREATED");

    private final PassportService passports;
    private final WalletService wallets;
    private final VerificationMapper mapper;
    private final HistoryQueryService historyQuery;
    private final HistoryMapper historyMapper;

    public PassportsController(PassportService passports, WalletService wallets, VerificationMapper mapper,
                               HistoryQueryService historyQuery, HistoryMapper historyMapper) {
        this.passports = passports;
        this.wallets = wallets;
        this.mapper = mapper;
        this.historyQuery = historyQuery;
        this.historyMapper = historyMapper;
    }

    @Override
    public ResponseEntity<JobRef> createRegionPassports(UUID regionId, CreateRegionPassports body) {
        UUID jobId = passports.createForRegion(regionId, new PassportService.BatchInput(body.getUserIds(),
                Boolean.TRUE.equals(body.getCreatePdf()), Boolean.TRUE.equals(body.getRenew()), body.getValidFrom(),
                body.getPaperSize() == null ? PassportPdfRenderer.Paper.A4
                        : PassportPdfRenderer.Paper.valueOf(body.getPaperSize().name()),
                Boolean.TRUE.equals(body.getNotifyUsers())));
        JobRef ref = new JobRef();
        ref.setJobId(jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ref);
    }

    @Override
    public ResponseEntity<Resource> downloadMyPassport() {
        return file(passports.downloadMine(), MediaType.APPLICATION_PDF, "foodsaver-passport.pdf");
    }

    @Override
    public ResponseEntity<Resource> getAppleWalletPass() {
        return file(wallets.applePass(), MediaType.parseMediaType("application/vnd.apple.pkpass"),
                "foodsaver-passport.pkpass");
    }

    @Override
    public ResponseEntity<GoogleWalletLink> getGoogleWalletLink() {
        GoogleWalletLink link = new GoogleWalletLink();
        link.setSaveUrl(wallets.googleSaveUrl());
        return ResponseEntity.ok(link);
    }

    @Override
    public ResponseEntity<PassportStatus> getMyPassportStatus() {
        return ResponseEntity.ok(mapper.toStatus(passports.myStatus()));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getPassportHistory(UUID userId, List<String> actions,
                                                               OffsetDateTime from, OffsetDateTime to, Integer page,
                                                               Integer size) {
        // readability (self or mayAdministrateUser) is checked by the USER history access rule
        HistoryFilter filter = new HistoryFilter(SubjectType.USER, userId, null, null,
                actions == null || actions.isEmpty() ? PASSPORT_ACTIONS
                        : actions.stream().filter(PASSPORT_ACTIONS::contains).toList(),
                TimeMapper.toInstant(from), TimeMapper.toInstant(to), page == null ? 0 : page,
                size == null ? 20 : size);
        return ResponseEntity.ok(historyMapper.toPage(historyQuery.list(filter)));
    }

    @Override
    public ResponseEntity<List<PassportRequestView>> listPassportRequests(UUID regionId, Boolean includeFulfilled) {
        return ResponseEntity.ok(passports.requests(regionId, Boolean.TRUE.equals(includeFulfilled)).stream()
                .map(mapper::toRequest).toList());
    }

    @Override
    public ResponseEntity<PassportStatus> requestPassport() {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toStatus(passports.request()));
    }

    private static ResponseEntity<Resource> file(byte[] content, MediaType type, String name) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build()
                        .toString())
                .body(new ByteArrayResource(content));
    }
}
