package network.foodsharing.store.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.HistoryFilter;
import network.foodsharing.history.SubjectType;
import network.foodsharing.history.domain.HistoryEntry;
import network.foodsharing.history.service.HistoryQueryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreLogAction;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store log (A21): STORE history entries of one store, filtered by date range and {@link StoreLogAction} codes. */
@Service
public class StoreLogService {

    static final Set<String> CODES = Arrays.stream(StoreLogAction.values()).map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    private final StoreService stores;
    private final StorePermissionService rights;
    private final CurrentActorService currentActor;
    private final HistoryQueryService historyQuery;

    public StoreLogService(StoreService stores, StorePermissionService rights, CurrentActorService currentActor,
                           HistoryQueryService historyQuery) {
        this.stores = stores;
        this.rights = rights;
        this.currentActor = currentActor;
        this.historyQuery = historyQuery;
    }

    /** @throws ApiException 404, 403 unless maySeeStoreLog, 400 VALIDATION_FAILED for unknown actions / from after to */
    @Transactional(readOnly = true)
    public Page<HistoryEntry> list(UUID storeId, Instant from, Instant to, List<String> actions, Integer page,
                                   Integer size) {
        Store store = stores.require(storeId);
        StorePermissionService.require(rights.rights(currentActor.requirePrincipal(), store).maySeeStoreLog());
        List<String> filter = validate(actions, from, to);
        return historyQuery.list(new HistoryFilter(SubjectType.STORE, storeId, null, null, filter, from, to,
                page == null ? 0 : page, size == null ? 20 : size));
    }

    static List<String> validate(List<String> actions, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        List<String> filter = actions == null ? List.of() : actions.stream().filter(a -> a != null && !a.isBlank())
                .map(String::strip).distinct().toList();
        if (!CODES.containsAll(filter)) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        return filter;
    }
}
