package network.foodsharing.report.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.report.domain.ReportAction;
import network.foodsharing.security.CurrentActorService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Report log of a region (D10.3): every REPORT history entry of the region, for its report handlers only. */
@Service
@Transactional(readOnly = true)
public class ReportLogService {

    static final List<String> ACTIONS = Arrays.stream(ReportAction.values()).map(ReportAction::code).toList();

    private final ReportPermissionService access;
    private final RegionService regions;
    private final HistoryQueryService historyQuery;
    private final CurrentActorService actors;

    public ReportLogService(ReportPermissionService access, RegionService regions, HistoryQueryService historyQuery,
                            CurrentActorService actors) {
        this.access = access;
        this.regions = regions;
        this.historyQuery = historyQuery;
        this.actors = actors;
    }

    /** @throws network.foodsharing.common.error.ApiException 404 REGION_NOT_FOUND, 403 REPORTS_NOT_ACCESSIBLE */
    public Page<HistoryEntry> log(UUID regionId, Instant from, Instant to, int page, int size) {
        regions.require(regionId);
        access.requireHandler(actors.requirePrincipal(), regionId);
        return historyQuery.listForRegion(SubjectType.REPORT, regionId, ACTIONS, from, to, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }
}
