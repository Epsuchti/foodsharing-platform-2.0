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
import network.foodsharing.api.model.GapCandidate;
import network.foodsharing.api.model.PickupPolicy;
import network.foodsharing.api.model.PlannedAdvert;
import network.foodsharing.api.model.RegionAutomationOverview;
import network.foodsharing.api.model.RequestPolicy;
import network.foodsharing.api.model.SignoutAlertSetting;
import network.foodsharing.api.model.StoreAutomation;
import network.foodsharing.automation.mapper.AutomationMapper;
import network.foodsharing.automation.service.AutomationAuditService;
import network.foodsharing.automation.service.AutomationOverviewService;
import network.foodsharing.automation.service.ExtraSlotService;
import network.foodsharing.automation.service.MetricArchiveService;
import network.foodsharing.automation.service.OpenSlotAdvertRunner;
import network.foodsharing.automation.service.OpenSlotAdvertService;
import network.foodsharing.automation.service.PickupCountGroupingService;
import network.foodsharing.automation.service.SignoutAlertService;
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
 * Store and region automations (M4 B4/B5): pickup and request policies, audits, cleaning exemptions, holidays,
 * open-slot adverts, sign-out alerts, extra slot, pickup-count grouping and metrics.
 */
@RestController
public class AutomationController implements AutomationApi {

    private final PickupPolicyService pickupPolicies;
    private final RequestPolicyService requestPolicies;
    private final AutomationAuditService audits;
    private final CleaningExemptionService exemptions;
    private final AutomationOverviewService overview;
    private final AutomationMapper mapper;
    private final java.time.Clock clock;
    private final OpenSlotAdvertService adverts;
    private final OpenSlotAdvertRunner advertRunner;
    private final SignoutAlertService signoutAlerts;
    private final ExtraSlotService extraSlots;
    private final PickupCountGroupingService grouping;
    private final MetricArchiveService metrics;
    private final network.foodsharing.pickup.mapper.PickupMapper pickupMapper;
    private final network.foodsharing.automation.service.LinkedOptionsService linkedOptions;
    private final network.foodsharing.automation.service.LinkCandidateService linkCandidates;

    public AutomationController(PickupPolicyService pickupPolicies, RequestPolicyService requestPolicies,
                                AutomationAuditService audits,
                                CleaningExemptionService exemptions, AutomationOverviewService overview,
                                AutomationMapper mapper, java.time.Clock clock, OpenSlotAdvertService adverts,
                                OpenSlotAdvertRunner advertRunner, SignoutAlertService signoutAlerts,
                                ExtraSlotService extraSlots, PickupCountGroupingService grouping,
                                MetricArchiveService metrics, network.foodsharing.pickup.mapper.PickupMapper pickupMapper,
                                network.foodsharing.automation.service.LinkedOptionsService linkedOptions,
                                network.foodsharing.automation.service.LinkCandidateService linkCandidates) {
        this.linkedOptions = linkedOptions;
        this.linkCandidates = linkCandidates;
        this.adverts = adverts;
        this.advertRunner = advertRunner;
        this.signoutAlerts = signoutAlerts;
        this.extraSlots = extraSlots;
        this.grouping = grouping;
        this.metrics = metrics;
        this.pickupMapper = pickupMapper;
        this.pickupPolicies = pickupPolicies;
        this.requestPolicies = requestPolicies;
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
        var added = extraSlots.add(storeId, network.foodsharing.pickup.mapper.PickupMapper.instant(pickupAt));
        return ResponseEntity.ok(pickupMapper.toModels(List.of(added.pickup())).getFirst());
    }

    @Override
    public ResponseEntity<Void> deleteOpenSlotAdvert(UUID storeId, Integer advertNumber) {
        adverts.delete(storeId, advertNumber);
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.ok(mapper.toSeries(metrics.series(regionId, from, to)));
    }

    @Override
    public ResponseEntity<OpenSlotAdvert> getOpenSlotAdvert(UUID storeId, Integer advertNumber) {
        return ResponseEntity.ok(mapper.toModel(adverts.get(storeId, advertNumber)));
    }

    @Override
    public ResponseEntity<List<PickupCountGroup>> getPickupCountGrouping(UUID regionId, Integer days) {
        return ResponseEntity.ok(mapper.toGroups(grouping.groups(regionId, days)));
    }

    @Override
    public ResponseEntity<RegionAutomationOverview> getRegionAutomation(UUID regionId) {
        RegionAutomationOverview model = new RegionAutomationOverview();
        model.setStores(overview.region(regionId).stream().map(mapper::toModel).toList());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<SignoutAlertSetting> getSignoutAlert(UUID storeId) {
        return ResponseEntity.ok(mapper.toModel(signoutAlerts.get(storeId)));
    }

    @Override
    public ResponseEntity<StoreAutomation> getStoreAutomation(UUID storeId) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        StoreAutomation model = new StoreAutomation();
        pickupPolicies.find(storeId).ifPresent(view -> model.setPickupPolicy(mapper.toModel(view)));
        requestPolicies.find(storeId).ifPresent(policy -> model.setRequestPolicy(mapper.toModel(policy)));
        model.setAdverts(adverts.list(storeId).stream().map(mapper::toModel).toList());
        signoutAlerts.find(storeId).ifPresent(view -> model.setSignoutAlert(mapper.toModel(view)));
        model.setExtraSlotAllowed(store.isExtraSlotAllowed());
        model.setCleaningFoodSharePoints(pickupPolicies.cleaningFoodSharePointsOf(store).stream()
                .map(AutomationMapper::toFspRef).toList());
        AutomationPermissions permissions = new AutomationPermissions();
        permissions.setMayEdit(true);
        permissions.setMaySeeAudits(true);
        model.setPermissions(permissions);
        model.setTelegramChatCount(overview.storeChatCount(storeId));
        model.setCleaningMonths(pickupPolicies.cleaningMonths());
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.LinkCandidatePage> searchLinkCandidates(UUID storeId, String q,
                                                                                            Integer page, Integer size) {
        var found = linkCandidates.search(storeId, q, page, size);
        return ResponseEntity.ok(new network.foodsharing.api.model.LinkCandidatePage()
                .items(found.getContent().stream().map(mapper::toModel).toList())
                .page(PageMapper.toPageMeta(found)));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.StoreRuleSummary> getStoreRules(UUID storeId) {
        return ResponseEntity.ok(mapper.toModel(pickupPolicies.rules(storeId)));
    }

    @Override
    public ResponseEntity<network.foodsharing.api.model.LinkedOptions> getLinkedOptions(UUID storeId,
                                                                                    java.time.OffsetDateTime pickupAt) {
        return ResponseEntity.ok(mapper.toLinkedOptions(linkedOptions.linkedOptions(storeId,
                network.foodsharing.pickup.mapper.PickupMapper.instant(pickupAt))));
    }

    @Override
    public ResponseEntity<List<GapCandidate>> listGapCandidates(UUID storeId) {
        return ResponseEntity.ok(pickupPolicies.gapCandidates(storeId).stream().map(mapper::toModel).toList());
    }

    @Override
    public ResponseEntity<List<Cancellation>> listCancellations(UUID storeId, LocalDate from, LocalDate to) {
        return ResponseEntity.ok(mapper.toCancellations(signoutAlerts.list(storeId, from, to)));
    }

    @Override
    public ResponseEntity<List<CleaningExemption>> listCleaningExemptions(UUID regionId) {
        return ResponseEntity.ok(mapper.toExemptions(exemptions.list(regionId)));
    }

    @Override
    public ResponseEntity<List<PlannedAdvert>> listPlannedAdverts(UUID storeId, Integer days) {
        return ResponseEntity.ok(mapper.toPlanned(adverts.planned(storeId, days)));
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
    public ResponseEntity<OpenSlotAdvert> putOpenSlotAdvert(UUID storeId, Integer advertNumber, OpenSlotAdvert openSlotAdvert) {
        return ResponseEntity.ok(mapper.toModel(adverts.put(storeId, advertNumber, mapper.toInput(openSlotAdvert))));
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
        return ResponseEntity.ok(mapper.toModel(signoutAlerts.put(storeId, mapper.toInput(signoutAlertSetting))));
    }

    @Override
    public ResponseEntity<Void> removeCleaningExemption(UUID regionId, UUID exemptionId) {
        exemptions.remove(regionId, exemptionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AutomationRunResult> runAdverts(UUID storeId) {
        pickupPolicies.requireAutomationStore(storeId);
        return ResponseEntity.ok(mapper.toModel(advertRunner.runStore(storeId)));
    }

    @Override
    public ResponseEntity<ExtraSlotSetting> setExtraSlotAllowed(UUID storeId, ExtraSlotSetting extraSlotSetting) {
        ExtraSlotSetting body = new ExtraSlotSetting();
        body.setAllowed(extraSlots.setAllowed(storeId, Boolean.TRUE.equals(extraSlotSetting.getAllowed())));
        return ResponseEntity.ok(body);
    }
}
