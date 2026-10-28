package network.foodsharing.automation.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AuditKind;
import network.foodsharing.automation.domain.PickupPolicyAudit;
import network.foodsharing.automation.domain.PickupPolicyDecision;
import network.foodsharing.automation.domain.PolicyRule;
import network.foodsharing.automation.domain.RequestDecision;
import network.foodsharing.automation.domain.RequestPolicyAudit;
import network.foodsharing.automation.repository.PickupPolicyAuditRepository;
import network.foodsharing.automation.repository.RequestPolicyAuditRepository;
import network.foodsharing.automation.service.PickupPolicyEvaluator.Reason;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StoreService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Automation decisions of a store or a region (default: the last 7 days). Region scope: ambassadors and
 * CAN_SEE_ALL_AUTOMATION_DECISIONS holders see all, everyone else only the decisions about themselves. Kinds PICKUP
 * and REQUEST are stored here; ADVERT, CANCELLATION and EXTRA_SLOT come with B5.
 */
@Service
public class AutomationAuditService {

    static final int DEFAULT_DAYS = 7;
    private static final List<PickupPolicyDecision> CONFIRMED_PICKUP = List.of(PickupPolicyDecision.CONFIRMED,
            PickupPolicyDecision.WOULD_CONFIRM);
    private static final List<RequestDecision> CONFIRMED_REQUEST = List.of(RequestDecision.APPROVED,
            RequestDecision.WOULD_APPROVE);
    private static final UUID NO_USER = new UUID(0L, 0L);

    /** One audit row of any kind. */
    public record AuditView(UUID id, AuditKind kind, UUID storeId, Instant pickupAt, UUID userId, String status,
                            boolean dryRun, List<Reason> reasons, String userMessage, String error, Instant createdAt) {
    }

    public record Query(AuditKind kind, boolean excludeConfirmed, LocalDate from, LocalDate to, Integer page,
                        Integer size) {
    }

    private final PickupPolicyAuditRepository pickupAudits;
    private final RequestPolicyAuditRepository requestAudits;
    private final PickupPolicyService pickupPolicies;
    private final StoreService stores;
    private final RegionService regions;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final ObjectMapper json;
    private final Clock clock;

    public AutomationAuditService(PickupPolicyAuditRepository pickupAudits, RequestPolicyAuditRepository requestAudits,
                                  PickupPolicyService pickupPolicies, StoreService stores, RegionService regions,
                                  PermissionService permissions, CurrentActorService currentActor, ObjectMapper json,
                                  Clock clock) {
        this.clock = clock;
        this.pickupAudits = pickupAudits;
        this.requestAudits = requestAudits;
        this.pickupPolicies = pickupPolicies;
        this.stores = stores;
        this.regions = regions;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.json = json;
    }

    /** @throws ApiException 403, 404 */
    @Transactional(readOnly = true)
    public Page<AuditView> storeAudits(UUID storeId, Query query) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        return search(List.of(storeId), null, query, regions.zoneOf(store.getRegionId()));
    }

    /** @throws ApiException 403 (not a member), 404 REGION_NOT_FOUND */
    @Transactional(readOnly = true)
    public Page<AuditView> regionAudits(UUID regionId, boolean onlyMine, Query query) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean seesAll = permissions.mayAdministrateRegion(actor, regionId)
                || permissions.has(actor, Permission.CAN_SEE_ALL_AUTOMATION_DECISIONS, regionId);
        if (!seesAll && !permissions.isMemberOf(actor, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
        List<UUID> storeIds = stores.liveStoresInRegions(regions.subtreeIds(regionId)).stream().map(Store::getId)
                .toList();
        return search(storeIds, onlyMine || !seesAll ? actor.userId() : null, query, regions.zoneOf(regionId));
    }

    Page<AuditView> search(List<UUID> storeIds, UUID userId, Query query, ZoneId zone) {
        int size = query.size() == null ? 20 : query.size();
        int page = query.page() == null ? 0 : query.page();
        if (size < 1 || size > 100 || page < 0) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "page");
        }
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        LocalDate fromDay = query.from() == null ? today.minusDays(DEFAULT_DAYS) : query.from();
        LocalDate toDay = query.to() == null ? today : query.to();
        if (fromDay.isAfter(toDay)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "from");
        }
        Instant from = fromDay.atStartOfDay(zone).toInstant();
        Instant to = toDay.plusDays(1).atStartOfDay(zone).toInstant();
        Pageable window = PageRequest.of(0, (page + 1) * size);
        List<AuditView> merged = new ArrayList<>();
        long total = 0;
        boolean allUsers = userId == null;
        UUID user = allUsers ? NO_USER : userId;
        if (!storeIds.isEmpty() && (query.kind() == null || query.kind() == AuditKind.PICKUP)) {
            Page<PickupPolicyAudit> rows = pickupAudits.search(storeIds, allUsers, user, query.excludeConfirmed(),
                    CONFIRMED_PICKUP, from, to, window);
            rows.forEach(row -> merged.add(view(row)));
            total += rows.getTotalElements();
        }
        if (!storeIds.isEmpty() && (query.kind() == null || query.kind() == AuditKind.REQUEST)) {
            Page<RequestPolicyAudit> rows = requestAudits.search(storeIds, allUsers, user, query.excludeConfirmed(),
                    CONFIRMED_REQUEST, from, to, window);
            rows.forEach(row -> merged.add(view(row)));
            total += rows.getTotalElements();
        }
        merged.sort(Comparator.comparing(AuditView::createdAt).reversed().thenComparing(AuditView::id));
        List<AuditView> content = merged.stream().skip((long) page * size).limit(size).toList();
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }


    private AuditView view(PickupPolicyAudit row) {
        return new AuditView(row.getId(), AuditKind.PICKUP, row.getStoreId(), row.getPickupAt(), row.getUserId(),
                row.getDecision().name(), row.isDryRun(), reasons(row.getReasons()), row.getUserMessage(),
                row.getError(), row.getCreatedAt());
    }

    private AuditView view(RequestPolicyAudit row) {
        List<Reason> reasons = row.getReason() == null ? List.of()
                : List.of(new Reason(PolicyRule.valueOf(row.getReason()), row.getMessage() == null ? ""
                : row.getMessage()));
        return new AuditView(row.getId(), AuditKind.REQUEST, row.getStoreId(), null, row.getUserId(),
                row.getStatus().name(), row.isDryRun(), reasons, row.getMessage(), row.getError(),
                row.getCreatedAt());
    }

    List<Reason> reasons(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(stored, new TypeReference<List<Reason>>() { });
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /** Store names for a page of audits. */
    @Transactional(readOnly = true)
    public Map<UUID, Store> storesOf(List<AuditView> audits) {
        return stores.liveStores(audits.stream().map(AuditView::storeId).distinct().toList()).stream()
                .collect(Collectors.toMap(Store::getId, s -> s));
    }
}
