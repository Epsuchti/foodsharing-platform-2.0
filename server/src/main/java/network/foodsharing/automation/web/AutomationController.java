package network.foodsharing.automation.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AutomationApi;
import network.foodsharing.api.model.AuditKind;
import network.foodsharing.api.model.AutomationAuditPage;
import network.foodsharing.api.model.AutomationPermissions;
import network.foodsharing.api.model.AutomationRunResult;
import network.foodsharing.api.model.Cancellation;
import network.foodsharing.api.model.CleaningExemption;
import network.foodsharing.api.model.CleaningExemptionCreate;
import network.foodsharing.api.model.ExtraSlotSetting;
import network.foodsharing.api.model.Holiday;
import network.foodsharing.api.model.MetricSeries;
import network.foodsharing.api.model.OpenSlotAdvert;
import network.foodsharing.api.model.Pickup;
import network.foodsharing.api.model.PickupCountGroup;
import network.foodsharing.api.model.PickupPolicy;
import network.foodsharing.api.model.PlannedAdvert;
import network.foodsharing.api.model.PolicyDecisionPreview;
import network.foodsharing.api.model.PolicyPreviewRequest;
import network.foodsharing.api.model.RegionAutomationOverview;
import network.foodsharing.api.model.RequestPolicy;
import network.foodsharing.api.model.SignoutAlertSetting;
import network.foodsharing.api.model.StoreAutomation;
import network.foodsharing.automation.mapper.AutomationMapper;
import network.foodsharing.automation.service.AutomationAuditService;
import network.foodsharing.automation.service.AutomationOverviewService;
import network.foodsharing.automation.service.AutomationRunService;
import network.foodsharing.automation.service.CleaningExemptionService;
import network.foodsharing.automation.service.PickupPolicyService;
import network.foodsharing.automation.service.RequestPolicyService;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.store.domain.Store;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store and region automations (M4 B4/B5). B4: pickup and request policies, audits, cleaning exemptions, holidays.
 * Operations of B5 answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class AutomationController implements AutomationApi {

    private final PickupPolicyService pickupPolicies;
    private final RequestPolicyService requestPolicies;
    private final AutomationRunService runs;
    private final AutomationAuditService audits;
    private final CleaningExemptionService exemptions;
    private final AutomationOverviewService overview;
    private final AutomationMapper mapper;
    private final java.time.Clock clock;

    public AutomationController(PickupPolicyService pickupPolicies, RequestPolicyService requestPolicies,
                                AutomationRunService runs, AutomationAuditService audits,
                                CleaningExemptionService exemptions, AutomationOverviewService overview,
                                AutomationMapper mapper, java.time.Clock clock) {
        this.pickupPolicies = pickupPolicies;
        this.requestPolicies = requestPolicies;
        this.runs = runs;
        this.audits = audits;
        this.exemptions = exemptions;
        this.overview = overview;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<CleaningExemption> addCleaningExemption(UUID regionId, CleaningExemptionCreate cleaningExemptionCreate) {
        var created = exemptions.add(regionId, cleaningExemptionCreate.getUserId(), cleaningExemptionCreate.getReason());
        return ResponseEntity.status(201).body(mapper.toExemptions(List.of(created)).getFirst());
    }

    @Override
    public ResponseEntity<Pickup> addExtraSlot(UUID storeId, OffsetDateTime pickupAt) {
        throw ApiException.notImplemented("addExtraSlot");
    }

    @Override
    public ResponseEntity<Void> deleteOpenSlotAdvert(UUID storeId, Integer advertNumber) {
        throw ApiException.notImplemented("deleteOpenSlotAdvert");
    }

    @Override
    public ResponseEntity<Void> deletePickupPolicy(UUID storeId) {
        pickupPolicies.delete(storeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> deleteRequestPolicy(UUID storeId) {
        requestPolicies.delete(storeId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<MetricSeries>> getAutomationMetrics(UUID regionId, LocalDate from, LocalDate to) {
        throw ApiException.notImplemented("getAutomationMetrics");
    }

    @Override
    public ResponseEntity<OpenSlotAdvert> getOpenSlotAdvert(UUID storeId, Integer advertNumber) {
        throw ApiException.notImplemented("getOpenSlotAdvert");
    }

    @Override
    public ResponseEntity<List<PickupCountGroup>> getPickupCountGrouping(UUID regionId, Integer days) {
        throw ApiException.notImplemented("getPickupCountGrouping");
    }

    @Override
    public ResponseEntity<RegionAutomationOverview> getRegionAutomation(UUID regionId) {
        RegionAutomationOverview model = new RegionAutomationOverview();
        model.setStores(overview.region(regionId).stream().map(mapper::toModel).toList());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<SignoutAlertSetting> getSignoutAlert(UUID storeId) {
        throw ApiException.notImplemented("getSignoutAlert");
    }

    @Override
    public ResponseEntity<StoreAutomation> getStoreAutomation(UUID storeId) {
        pickupPolicies.requireAutomationStore(storeId);
        StoreAutomation model = new StoreAutomation();
        pickupPolicies.find(storeId).ifPresent(view -> model.setPickupPolicy(mapper.toModel(view)));
        requestPolicies.find(storeId).ifPresent(policy -> model.setRequestPolicy(mapper.toModel(policy)));
        model.setAdverts(List.of());
        model.setExtraSlotAllowed(false);
        AutomationPermissions permissions = new AutomationPermissions();
        permissions.setMayEdit(true);
        permissions.setMaySeeAudits(true);
        model.setPermissions(permissions);
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<List<Cancellation>> listCancellations(UUID storeId, LocalDate from, LocalDate to) {
        throw ApiException.notImplemented("listCancellations");
    }

    @Override
    public ResponseEntity<List<CleaningExemption>> listCleaningExemptions(UUID regionId) {
        return ResponseEntity.ok(mapper.toExemptions(exemptions.list(regionId)));
    }

    @Override
    public ResponseEntity<List<PlannedAdvert>> listPlannedAdverts(UUID storeId, Integer days) {
        throw ApiException.notImplemented("listPlannedAdverts");
    }

    @Override
    public ResponseEntity<AutomationAuditPage> listRegionAutomationAudits(UUID regionId, Boolean onlyMine, AuditKind kind, Boolean excludeConfirmed, LocalDate from, LocalDate to, Integer page, Integer size) {
        return ResponseEntity.ok(page(audits.regionAudits(regionId, Boolean.TRUE.equals(onlyMine),
                query(kind, excludeConfirmed, from, to, page, size))));
    }

    @Override
    public ResponseEntity<List<Holiday>> listRegionHolidays(UUID regionId, Integer year) {
        int wanted = year == null ? LocalDate.now(clock).getYear() : year;
        return ResponseEntity.ok(mapper.toHolidays(overview.holidays(regionId, wanted)));
    }

    @Override
    public ResponseEntity<AutomationAuditPage> listStoreAutomationAudits(UUID storeId, AuditKind kind, Boolean excludeConfirmed, LocalDate from, LocalDate to, Integer page, Integer size) {
        return ResponseEntity.ok(page(audits.storeAudits(storeId, query(kind, excludeConfirmed, from, to, page, size))));
    }

    private static AutomationAuditService.Query query(AuditKind kind, Boolean excludeConfirmed, LocalDate from,
                                                      LocalDate to, Integer page, Integer size) {
        return new AutomationAuditService.Query(kind == null ? null
                : network.foodsharing.automation.domain.AuditKind.valueOf(kind.name()),
                Boolean.TRUE.equals(excludeConfirmed), from, to, page, size);
    }

    private AutomationAuditPage page(Page<AutomationAuditService.AuditView> result) {
        java.util.Map<UUID, Store> stores = audits.storesOf(result.getContent());
        AutomationAuditPage model = new AutomationAuditPage();
        model.setItems(mapper.toAudits(result.getContent(), stores));
        model.setPage(PageMapper.toPageMeta(result));
        return model;
    }

    @Override
    public ResponseEntity<PolicyDecisionPreview> previewPickupPolicy(UUID storeId, PolicyPreviewRequest policyPreviewRequest) {
        return ResponseEntity.ok(mapper.toModel(runs.preview(storeId, policyPreviewRequest.getPickupAt().toInstant(),
                policyPreviewRequest.getUserId())));
    }

    @Override
    public ResponseEntity<OpenSlotAdvert> putOpenSlotAdvert(UUID storeId, Integer advertNumber, OpenSlotAdvert openSlotAdvert) {
        throw ApiException.notImplemented("putOpenSlotAdvert");
    }

    @Override
    public ResponseEntity<PickupPolicy> putPickupPolicy(UUID storeId, PickupPolicy pickupPolicy) {
        return ResponseEntity.ok(mapper.toModel(pickupPolicies.put(storeId, mapper.toInput(pickupPolicy))));
    }

    @Override
    public ResponseEntity<RequestPolicy> putRequestPolicy(UUID storeId, RequestPolicy requestPolicy) {
        return ResponseEntity.ok(mapper.toModel(requestPolicies.put(storeId, mapper.toInput(requestPolicy))));
    }

    @Override
    public ResponseEntity<SignoutAlertSetting> putSignoutAlert(UUID storeId, SignoutAlertSetting signoutAlertSetting) {
        throw ApiException.notImplemented("putSignoutAlert");
    }

    @Override
    public ResponseEntity<Void> removeCleaningExemption(UUID regionId, UUID exemptionId) {
        exemptions.remove(regionId, exemptionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AutomationRunResult> runAdverts(UUID storeId, Boolean dryRun) {
        throw ApiException.notImplemented("runAdverts");
    }

    @Override
    public ResponseEntity<AutomationRunResult> runPickupPolicy(UUID storeId, Boolean dryRun) {
        return ResponseEntity.ok(mapper.toModel(runs.runPickupPolicy(storeId, Boolean.TRUE.equals(dryRun))));
    }

    @Override
    public ResponseEntity<AutomationRunResult> runRequestPolicy(UUID storeId, Boolean dryRun) {
        return ResponseEntity.ok(mapper.toModel(runs.runRequestPolicy(storeId, Boolean.TRUE.equals(dryRun))));
    }

    @Override
    public ResponseEntity<ExtraSlotSetting> setExtraSlotAllowed(UUID storeId, ExtraSlotSetting extraSlotSetting) {
        throw ApiException.notImplemented("setExtraSlotAllowed");
    }
}
