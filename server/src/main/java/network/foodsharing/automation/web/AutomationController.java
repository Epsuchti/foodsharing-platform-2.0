package network.foodsharing.automation.web;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import network.foodsharing.api.AutomationApi;
import network.foodsharing.api.model.AuditKind;
import network.foodsharing.api.model.AutomationAuditPage;
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
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store and region automations (M4 B4/B5).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m4.md).
 */
@RestController
public class AutomationController implements AutomationApi {

    @Override
    public ResponseEntity<CleaningExemption> addCleaningExemption(UUID regionId, CleaningExemptionCreate cleaningExemptionCreate) {
        throw ApiException.notImplemented("addCleaningExemption");
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
        throw ApiException.notImplemented("deletePickupPolicy");
    }

    @Override
    public ResponseEntity<Void> deleteRequestPolicy(UUID storeId) {
        throw ApiException.notImplemented("deleteRequestPolicy");
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
        throw ApiException.notImplemented("getRegionAutomation");
    }

    @Override
    public ResponseEntity<SignoutAlertSetting> getSignoutAlert(UUID storeId) {
        throw ApiException.notImplemented("getSignoutAlert");
    }

    @Override
    public ResponseEntity<StoreAutomation> getStoreAutomation(UUID storeId) {
        throw ApiException.notImplemented("getStoreAutomation");
    }

    @Override
    public ResponseEntity<List<Cancellation>> listCancellations(UUID storeId, LocalDate from, LocalDate to) {
        throw ApiException.notImplemented("listCancellations");
    }

    @Override
    public ResponseEntity<List<CleaningExemption>> listCleaningExemptions(UUID regionId) {
        throw ApiException.notImplemented("listCleaningExemptions");
    }

    @Override
    public ResponseEntity<List<PlannedAdvert>> listPlannedAdverts(UUID storeId, Integer days) {
        throw ApiException.notImplemented("listPlannedAdverts");
    }

    @Override
    public ResponseEntity<AutomationAuditPage> listRegionAutomationAudits(UUID regionId, Boolean onlyMine, AuditKind kind, Boolean excludeConfirmed, LocalDate from, LocalDate to, Integer page, Integer size) {
        throw ApiException.notImplemented("listRegionAutomationAudits");
    }

    @Override
    public ResponseEntity<List<Holiday>> listRegionHolidays(UUID regionId, Integer year) {
        throw ApiException.notImplemented("listRegionHolidays");
    }

    @Override
    public ResponseEntity<AutomationAuditPage> listStoreAutomationAudits(UUID storeId, AuditKind kind, Boolean excludeConfirmed, LocalDate from, LocalDate to, Integer page, Integer size) {
        throw ApiException.notImplemented("listStoreAutomationAudits");
    }

    @Override
    public ResponseEntity<PolicyDecisionPreview> previewPickupPolicy(UUID storeId, PolicyPreviewRequest policyPreviewRequest) {
        throw ApiException.notImplemented("previewPickupPolicy");
    }

    @Override
    public ResponseEntity<OpenSlotAdvert> putOpenSlotAdvert(UUID storeId, Integer advertNumber, OpenSlotAdvert openSlotAdvert) {
        throw ApiException.notImplemented("putOpenSlotAdvert");
    }

    @Override
    public ResponseEntity<PickupPolicy> putPickupPolicy(UUID storeId, PickupPolicy pickupPolicy) {
        throw ApiException.notImplemented("putPickupPolicy");
    }

    @Override
    public ResponseEntity<RequestPolicy> putRequestPolicy(UUID storeId, RequestPolicy requestPolicy) {
        throw ApiException.notImplemented("putRequestPolicy");
    }

    @Override
    public ResponseEntity<SignoutAlertSetting> putSignoutAlert(UUID storeId, SignoutAlertSetting signoutAlertSetting) {
        throw ApiException.notImplemented("putSignoutAlert");
    }

    @Override
    public ResponseEntity<Void> removeCleaningExemption(UUID regionId, UUID exemptionId) {
        throw ApiException.notImplemented("removeCleaningExemption");
    }

    @Override
    public ResponseEntity<AutomationRunResult> runAdverts(UUID storeId, Boolean dryRun) {
        throw ApiException.notImplemented("runAdverts");
    }

    @Override
    public ResponseEntity<AutomationRunResult> runPickupPolicy(UUID storeId, Boolean dryRun) {
        throw ApiException.notImplemented("runPickupPolicy");
    }

    @Override
    public ResponseEntity<AutomationRunResult> runRequestPolicy(UUID storeId, Boolean dryRun) {
        throw ApiException.notImplemented("runRequestPolicy");
    }

    @Override
    public ResponseEntity<ExtraSlotSetting> setExtraSlotAllowed(UUID storeId, ExtraSlotSetting extraSlotSetting) {
        throw ApiException.notImplemented("setExtraSlotAllowed");
    }
}
