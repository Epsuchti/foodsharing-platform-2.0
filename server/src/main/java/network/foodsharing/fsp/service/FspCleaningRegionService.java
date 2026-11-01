package network.foodsharing.fsp.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.fsp.domain.FoodSharePoint;
import network.foodsharing.fsp.domain.FspAction;
import network.foodsharing.fsp.domain.FspStatus;
import network.foodsharing.fsp.domain.RegionCleaningFsp;
import network.foodsharing.fsp.repository.FoodSharePointRepository;
import network.foodsharing.fsp.repository.RegionCleaningFspRepository;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Fairteiler of a region that count for the cleaning rule (FS2, AU1). Edited by the region's ambassadors and
 * whoever has CAN_USE_AUTOMATIONS there (D8, as cleaning exemptions). Only ACTIVE, live Fairteiler of the region
 * itself with cleaning shifts can be linked (D7); switching cleaning off, deactivating or deleting a Fairteiler removes
 * its links with history.
 */
@Service
public class FspCleaningRegionService {

    /** The selected and the selectable Fairteiler of a region (by name). */
    public record Selection(List<FoodSharePoint> selected, List<FoodSharePoint> candidates) {
    }

    private static final Comparator<FoodSharePoint> BY_NAME = Comparator.comparing(FoodSharePoint::getName,
            String.CASE_INSENSITIVE_ORDER).thenComparing(FoodSharePoint::getId);

    private final RegionCleaningFspRepository links;
    private final FoodSharePointRepository fsps;
    private final RegionService regions;
    private final PermissionService permissions;
    private final CurrentActorService actors;
    private final HistoryService history;

    public FspCleaningRegionService(RegionCleaningFspRepository links, FoodSharePointRepository fsps,
                                    RegionService regions, PermissionService permissions, CurrentActorService actors,
                                    HistoryService history) {
        this.links = links;
        this.fsps = fsps;
        this.regions = regions;
        this.permissions = permissions;
        this.actors = actors;
        this.history = history;
    }

    /** @throws ApiException 404 REGION_NOT_FOUND, 403 */
    @Transactional(readOnly = true)
    public Selection get(UUID regionId) {
        requireEditor(regionId);
        return new Selection(selected(regionId), candidates(regionId));
    }

    /**
     * Replaces the region's cleaning Fairteiler; one history entry REGION_CLEANING_FSPS_CHANGED per added or removed
     * Fairteiler (subject = Fairteiler, region set).
     *
     * @throws ApiException 404 REGION_NOT_FOUND, 403, 400 VALIDATION_FAILED / FSP_NOT_CLEANING_CANDIDATE (args.fspId)
     */
    @Transactional
    public Selection replace(UUID regionId, List<UUID> fspIds) {
        requireEditor(regionId);
        Set<UUID> wanted = new LinkedHashSet<>(fspIds == null ? List.of() : fspIds);
        if (wanted.contains(null) || wanted.size() > 50) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "fspIds");
        }
        Map<UUID, FoodSharePoint> byId = fsps.findAllById(wanted).stream()
                .collect(Collectors.toMap(FoodSharePoint::getId, Function.identity()));
        for (UUID id : wanted) {
            FoodSharePoint fsp = byId.get(id);
            if (fsp == null || !candidate(regionId, fsp)) {
                throw ApiException.badRequest(ApiErrorCode.FSP_NOT_CLEANING_CANDIDATE).withArg("fspId", id.toString());
            }
        }
        List<RegionCleaningFsp> current = links.findByRegionId(regionId);
        Set<UUID> existing = current.stream().map(RegionCleaningFsp::getFspId).collect(Collectors.toSet());
        for (RegionCleaningFsp link : current) {
            if (!wanted.contains(link.getFspId())) {
                links.delete(link);
                history.record(history.event(FspAction.REGION_CLEANING_FSPS_CHANGED, link.getFspId())
                        .region(regionId).change("cleaningRule", true, false).build());
            }
        }
        for (UUID id : wanted) {
            if (existing.contains(id)) {
                continue;
            }
            RegionCleaningFsp link = new RegionCleaningFsp();
            link.setRegionId(regionId);
            link.setFspId(id);
            links.save(link);
            history.record(history.event(FspAction.REGION_CLEANING_FSPS_CHANGED, id).region(regionId)
                    .change("cleaningRule", false, true).build());
        }
        links.flush();
        return new Selection(selected(regionId), candidates(regionId));
    }

    /**
     * D7: drops every region link of a Fairteiler that stopped being a candidate (cleaning off, deactivated, deleted);
     * the caller's transaction records the reason. Returns the number of removed links.
     */
    @Transactional
    public int removeLinks(FoodSharePoint fsp, String reason) {
        List<RegionCleaningFsp> rows = links.findByFspId(fsp.getId());
        for (RegionCleaningFsp link : rows) {
            links.delete(link);
            history.record(history.event(FspAction.REGION_CLEANING_FSPS_CHANGED, fsp.getId())
                    .region(link.getRegionId()).change("cleaningRule", true, false).reason(reason).build());
        }
        return rows.size();
    }

    /** The region's cleaning Fairteiler that still qualify (no permission check). */
    @Transactional(readOnly = true)
    public List<FoodSharePoint> selected(UUID regionId) {
        List<UUID> ids = links.findByRegionId(regionId).stream().map(RegionCleaningFsp::getFspId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<FoodSharePoint> result = new ArrayList<>(fsps.findAllById(ids).stream()
                .filter(f -> candidate(regionId, f)).toList());
        result.sort(BY_NAME);
        return List.copyOf(result);
    }

    private List<FoodSharePoint> candidates(UUID regionId) {
        return fsps.findByRegionIdAndStatusAndCleaningEnabledTrueAndDeletedAtIsNullOrderByNameAscIdAsc(regionId,
                FspStatus.ACTIVE);
    }

    private static boolean candidate(UUID regionId, FoodSharePoint fsp) {
        return FspCleaningRules.isRegionCandidate(regionId, fsp.getRegionId(), fsp.getStatus(),
                fsp.getDeletedAt() != null, fsp.isCleaningEnabled());
    }

    private void requireEditor(UUID regionId) {
        regions.require(regionId);
        SessionPrincipal actor = actors.requirePrincipal();
        if (!permissions.mayAdministrateRegion(actor, regionId)
                && !permissions.has(actor, Permission.CAN_USE_AUTOMATIONS, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }
}
