package network.foodsharing.automation.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.automation.domain.CleaningRuleExemption;
import network.foodsharing.automation.repository.CleaningRuleExemptionRepository;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.history.HistoryAccessRule;
import network.foodsharing.history.SubjectType;
import network.foodsharing.security.Permission;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** AUTOMATION history: mayUseAutomations on the policy's store; exemptions: region automation rights (ORGA always). */
@Component
public class AutomationHistoryAccessRule implements HistoryAccessRule {

    private final PickupPolicyRepository pickupPolicies;
    private final RequestPolicyRepository requestPolicies;
    private final CleaningRuleExemptionRepository exemptions;
    private final StoreService stores;
    private final StorePermissionService storePermissions;
    private final PermissionService permissions;

    public AutomationHistoryAccessRule(PickupPolicyRepository pickupPolicies, RequestPolicyRepository requestPolicies,
                                       CleaningRuleExemptionRepository exemptions, StoreService stores,
                                       StorePermissionService storePermissions, PermissionService permissions) {
        this.pickupPolicies = pickupPolicies;
        this.requestPolicies = requestPolicies;
        this.exemptions = exemptions;
        this.stores = stores;
        this.storePermissions = storePermissions;
        this.permissions = permissions;
    }

    @Override
    public SubjectType subjectType() {
        return SubjectType.AUTOMATION;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canRead(SessionPrincipal actor, UUID subjectId) {
        if (permissions.isOrga(actor)) {
            return true;
        }
        Optional<UUID> storeId = pickupPolicies.findById(subjectId).map(p -> p.getStoreId())
                .or(() -> requestPolicies.findById(subjectId).map(p -> p.getStoreId()));
        if (storeId.isPresent()) {
            return stores.exists(storeId.get())
                    && storePermissions.mayUseAutomations(actor, stores.require(storeId.get()));
        }
        Optional<CleaningRuleExemption> exemption = exemptions.findById(subjectId);
        return exemption.map(e -> permissions.mayAdministrateRegion(actor, e.getRegionId())
                || permissions.has(actor, Permission.CAN_USE_AUTOMATIONS, e.getRegionId())).orElse(false);
    }
}
