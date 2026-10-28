package network.foodsharing.automation.service;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.CleaningRuleExemption;
import network.foodsharing.automation.repository.CleaningRuleExemptionRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Users of a region exempt from the cleaning rule (ambassador or CAN_USE_AUTOMATIONS). */
@Service
public class CleaningExemptionService {

    private final CleaningRuleExemptionRepository exemptions;
    private final RegionService regions;
    private final UserService users;
    private final PermissionService permissions;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public CleaningExemptionService(CleaningRuleExemptionRepository exemptions, RegionService regions,
                                    UserService users, PermissionService permissions,
                                    CurrentActorService currentActor, HistoryService history) {
        this.exemptions = exemptions;
        this.regions = regions;
        this.users = users;
        this.permissions = permissions;
        this.currentActor = currentActor;
        this.history = history;
    }

    /** Ambassador of the region or CAN_USE_AUTOMATIONS there. @throws ApiException 404 REGION_NOT_FOUND, 403 */
    @Transactional(readOnly = true)
    public void requireRegionAutomation(UUID regionId) {
        regions.require(regionId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        if (!permissions.mayAdministrateRegion(actor, regionId)
                && !permissions.has(actor, Permission.CAN_USE_AUTOMATIONS, regionId)) {
            throw ApiException.forbidden(ApiErrorCode.ACCESS_DENIED);
        }
    }

    @Transactional(readOnly = true)
    public List<CleaningRuleExemption> list(UUID regionId) {
        requireRegionAutomation(regionId);
        return exemptions.findByRegionIdOrderByCreatedAt(regionId);
    }

    /** @throws ApiException 403, 404 REGION_NOT_FOUND / USER_NOT_FOUND, 409 CLEANING_EXEMPTION_EXISTS */
    @Transactional
    public CleaningRuleExemption add(UUID regionId, UUID userId, String reason) {
        requireRegionAutomation(regionId);
        users.require(userId);
        if (reason != null && reason.length() > 500) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "reason");
        }
        if (exemptions.existsByRegionIdAndUserId(regionId, userId)) {
            throw ApiException.conflict(ApiErrorCode.CLEANING_EXEMPTION_EXISTS);
        }
        CleaningRuleExemption exemption = new CleaningRuleExemption();
        exemption.setRegionId(regionId);
        exemption.setUserId(userId);
        exemption.setReason(reason == null || reason.isBlank() ? null : reason.strip());
        exemption = exemptions.save(exemption);
        history.record(history.event(AutomationAction.CLEANING_EXEMPTION_ADDED, exemption.getId()).region(regionId)
                .secondarySubject(userId).reason(exemption.getReason()).build());
        return exemption;
    }

    /** @throws ApiException 403, 404 REGION_NOT_FOUND / EXEMPTION_NOT_FOUND */
    @Transactional
    public void remove(UUID regionId, UUID exemptionId) {
        requireRegionAutomation(regionId);
        CleaningRuleExemption exemption = exemptions.findByIdAndRegionId(exemptionId, regionId)
                .orElseThrow(() -> ApiException.notFound(ApiErrorCode.EXEMPTION_NOT_FOUND));
        exemptions.delete(exemption);
        history.record(history.event(AutomationAction.CLEANING_EXEMPTION_REMOVED, exemption.getId())
                .region(regionId).secondarySubject(exemption.getUserId()).build());
    }
}
