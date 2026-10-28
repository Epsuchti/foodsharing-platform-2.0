package network.foodsharing.store.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StoreChainsApi;
import network.foodsharing.api.model.StoreChain;
import network.foodsharing.api.model.StoreChainPage;
import network.foodsharing.api.model.StoreChainStatus;
import network.foodsharing.api.model.StoreChainUpdate;
import network.foodsharing.api.model.StoreListItem;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.store.mapper.StoreMapper;
import network.foodsharing.store.service.StoreChainService;
import network.foodsharing.store.service.StoreQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Store chain API (B7). */
@RestController
public class StoreChainsController implements StoreChainsApi {

    private final StoreChainService chains;
    private final StoreQueryService queries;
    private final StoreMapper mapper;

    public StoreChainsController(StoreChainService chains, StoreQueryService queries, StoreMapper mapper) {
        this.chains = chains;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<StoreChain> createStoreChain(StoreChainUpdate body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toChain(chains.create(mapper.toChainInput(body))));
    }

    @Override
    public ResponseEntity<StoreChain> getStoreChain(UUID chainId) {
        return ResponseEntity.ok(mapper.toChain(chains.get(chainId)));
    }

    @Override
    public ResponseEntity<List<StoreListItem>> listChainStores(UUID chainId) {
        chains.requireChainStoresAccess(chainId);
        return ResponseEntity.ok(queries.chainStores(chainId).stream().map(mapper::toListItem).toList());
    }

    @Override
    public ResponseEntity<StoreChainPage> listStoreChains(List<StoreChainStatus> status, String search, Integer page,
                                                          Integer size) {
        var result = chains.list(status == null ? null : status.stream()
                .map(s -> network.foodsharing.store.domain.StoreChainStatus.valueOf(s.name())).toList(), search, page,
                size);
        StoreChainPage model = new StoreChainPage();
        model.setItems(result.getContent().stream().map(mapper::toChain).toList());
        model.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(model);
    }

    @Override
    public ResponseEntity<StoreChain> updateStoreChain(UUID chainId, StoreChainUpdate body) {
        return ResponseEntity.ok(mapper.toChain(chains.update(chainId, mapper.toChainInput(body))));
    }
}
