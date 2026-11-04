package network.foodsharing.automation.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.automation.domain.PickupPolicy;
import network.foodsharing.automation.repository.PickupPolicyRepository;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreQueryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "Betrieb verknüpfen" picker (decision 165 S4, Q3): stores a store manager may link, found by name across regions
 * and bounded by what they may know (StoreVisibility, decision 156). Each row says whether the store checks sign-ups
 * itself and whether the actor may edit it, which a bidirectional rule needs.
 */
@Service
public class LinkCandidateService {

    /** Minimum search length (the contract's minLength of {@code q}). */
    static final int MINIMUM_QUERY = 2;

    public record Candidate(Store store, List<String> regionPath, boolean automationEnabled, boolean mayEdit) {
    }

    private final PickupPolicyService policies;
    private final PickupPolicyRepository policyRows;
    private final StoreQueryService storeQueries;
    private final StorePermissionService permissions;
    private final CurrentActorService currentActor;

    public LinkCandidateService(PickupPolicyService policies, PickupPolicyRepository policyRows,
                                StoreQueryService storeQueries, StorePermissionService permissions,
                                CurrentActorService currentActor) {
        this.policies = policies;
        this.policyRows = policyRows;
        this.storeQueries = storeQueries;
        this.permissions = permissions;
        this.currentActor = currentActor;
    }

    /** @throws ApiException 404 STORE_NOT_FOUND, 403 ACCESS_DENIED (mayEditStore), 400 VALIDATION_FAILED (q) */
    @Transactional(readOnly = true)
    public Page<Candidate> search(UUID storeId, String q, Integer page, Integer size) {
        policies.requireAutomationStore(storeId);
        if (q == null || q.strip().length() < MINIMUM_QUERY) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED).withArg("field", "q");
        }
        Page<StoreQueryService.KnownStore> found = storeQueries.searchKnown(q, storeId, page == null ? 0 : page,
                size == null ? 20 : size);
        List<Store> stores = found.getContent().stream().map(StoreQueryService.KnownStore::store).toList();
        List<UUID> ids = stores.stream().map(Store::getId).toList();
        Set<UUID> enabled = ids.isEmpty() ? Set.of() : policyRows.findByStoreIdIn(ids).stream()
                .filter(PickupPolicy::isEnabled).map(PickupPolicy::getStoreId).collect(Collectors.toSet());
        Set<UUID> editable = permissions.editableStoreIds(currentActor.requirePrincipal(), stores);
        return found.map(known -> new Candidate(known.store(), known.regionPath(),
                enabled.contains(known.store().getId()), editable.contains(known.store().getId())));
    }
}
