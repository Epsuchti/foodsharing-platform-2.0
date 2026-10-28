package network.foodsharing.automation.service;

import java.time.Instant;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Service;

/** "Run now" and preview for the store automation page (mayUseAutomations). */
@Service
public class AutomationRunService {

    private final PickupPolicyService pickupPolicies;
    private final RequestPolicyService requestPolicies;
    private final PickupPolicyRunner pickupRunner;
    private final RequestPolicyRunner requestRunner;
    private final UserService users;

    public AutomationRunService(PickupPolicyService pickupPolicies, RequestPolicyService requestPolicies,
                                PickupPolicyRunner pickupRunner, RequestPolicyRunner requestRunner,
                                UserService users) {
        this.pickupPolicies = pickupPolicies;
        this.requestPolicies = requestPolicies;
        this.pickupRunner = pickupRunner;
        this.requestRunner = requestRunner;
        this.users = users;
    }

    /** @throws ApiException 403, 404, 409 POLICY_NOT_FOUND */
    public PickupPolicyRunner.RunResult runPickupPolicy(UUID storeId, boolean dryRun) {
        pickupPolicies.requireAutomationStore(storeId);
        if (pickupPolicies.find(storeId).isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.POLICY_NOT_FOUND);
        }
        return pickupRunner.runStore(storeId, dryRun);
    }

    /** @throws ApiException 403, 404, 409 POLICY_NOT_FOUND */
    public PickupPolicyRunner.RunResult runRequestPolicy(UUID storeId, boolean dryRun) {
        pickupPolicies.requireAutomationStore(storeId);
        if (requestPolicies.find(storeId).isEmpty()) {
            throw ApiException.conflict(ApiErrorCode.POLICY_NOT_FOUND);
        }
        return requestRunner.runStore(storeId, dryRun);
    }

    /** Evaluates a (possibly hypothetical) sign-up without acting. @throws ApiException 403, 404 */
    public PickupPolicyEvaluator.Decision preview(UUID storeId, Instant pickupAt, UUID userId) {
        pickupPolicies.requireAutomationStore(storeId);
        users.require(userId);
        PickupPolicy policy = pickupPolicies.require(storeId);
        return pickupRunner.preview(policy, pickupAt, userId);
    }
}
