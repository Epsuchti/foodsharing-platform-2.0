package network.foodsharing.automation.service;

import java.util.Optional;
import java.util.UUID;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.AutomationAction;
import network.foodsharing.automation.domain.RequestPolicy;
import network.foodsharing.automation.repository.RequestPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.store.domain.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Request policy CRUD (AU2). */
@Service
public class RequestPolicyService {

    static final double MAXIMUM_DISTANCE_KM = 200;

    public record RequestPolicyInput(boolean enabled, boolean dryRun, boolean distanceRuleEnabled,
                                     double maximumDistanceKm, Long version) {
    }

    private final RequestPolicyRepository policies;
    private final PickupPolicyService pickupPolicies;
    private final HistoryService history;

    public RequestPolicyService(RequestPolicyRepository policies, PickupPolicyService pickupPolicies,
                                HistoryService history) {
        this.policies = policies;
        this.pickupPolicies = pickupPolicies;
        this.history = history;
    }

    @Transactional(readOnly = true)
    public Optional<RequestPolicy> find(UUID storeId) {
        return policies.findByStoreId(storeId);
    }

    /** @throws ApiException 404 POLICY_NOT_FOUND */
    @Transactional(readOnly = true)
    public RequestPolicy require(UUID storeId) {
        return policies.findByStoreId(storeId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.POLICY_NOT_FOUND));
    }

    /** @throws ApiException 400 VALIDATION_FAILED, 403, 404, 409 CONCURRENT_MODIFICATION */
    @Transactional
    public RequestPolicy put(UUID storeId, RequestPolicyInput input) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        if (Double.isNaN(input.maximumDistanceKm()) || input.maximumDistanceKm() < 0
                || input.maximumDistanceKm() > MAXIMUM_DISTANCE_KM) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "maximumDistanceKm");
        }
        Optional<RequestPolicy> existing = policies.findByStoreId(storeId);
        if (existing.isPresent() && input.version() != null && input.version() != existing.get().getVersion()) {
            throw ApiException.conflict(ApiErrorCode.CONCURRENT_MODIFICATION);
        }
        RequestPolicy policy = existing.orElseGet(RequestPolicy::new);
        policy.setStoreId(storeId);
        policy.setRegionId(store.getRegionId());
        policy.setEnabled(input.enabled());
        policy.setDryRun(input.dryRun());
        policy.setDistanceRuleEnabled(input.distanceRuleEnabled());
        policy.setMaximumDistanceKm(input.maximumDistanceKm());
        policy = policies.saveAndFlush(policy);
        history.record(history.event(AutomationAction.REQUEST_POLICY_UPDATED, policy.getId())
                .region(store.getRegionId()).metadata("storeId", storeId.toString()).build());
        return policy;
    }

    /** @throws ApiException 403, 404 (store or POLICY_NOT_FOUND) */
    @Transactional
    public void delete(UUID storeId) {
        Store store = pickupPolicies.requireAutomationStore(storeId);
        RequestPolicy policy = require(storeId);
        policies.delete(policy);
        history.record(history.event(AutomationAction.REQUEST_POLICY_UPDATED, policy.getId())
                .region(store.getRegionId()).metadata("storeId", storeId.toString()).metadata("deleted", "true")
                .build());
    }
}
