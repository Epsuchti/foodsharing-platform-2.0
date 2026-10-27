package network.foodsharing.store.web;

import java.util.List;
import java.util.UUID;
import network.foodsharing.api.StoreChainsApi;
import network.foodsharing.api.model.StoreChain;
import network.foodsharing.api.model.StoreChainPage;
import network.foodsharing.api.model.StoreChainStatus;
import network.foodsharing.api.model.StoreChainUpdate;
import network.foodsharing.api.model.StoreListItem;
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Store chain API (B7).
 * Operations answer 501 NOT_IMPLEMENTED until their task implements them (docs/plan-m3.md).
 */
@RestController
public class StoreChainsController implements StoreChainsApi {

    @Override
    public ResponseEntity<StoreChain> createStoreChain(StoreChainUpdate storeChainUpdate) {
        throw ApiException.notImplemented("createStoreChain");
    }

    @Override
    public ResponseEntity<StoreChain> getStoreChain(UUID chainId) {
        throw ApiException.notImplemented("getStoreChain");
    }

    @Override
    public ResponseEntity<List<StoreListItem>> listChainStores(UUID chainId) {
        throw ApiException.notImplemented("listChainStores");
    }

    @Override
    public ResponseEntity<StoreChainPage> listStoreChains(List<StoreChainStatus> status, String search, Integer page, Integer size) {
        throw ApiException.notImplemented("listStoreChains");
    }

    @Override
    public ResponseEntity<StoreChain> updateStoreChain(UUID chainId, StoreChainUpdate storeChainUpdate) {
        throw ApiException.notImplemented("updateStoreChain");
    }
}
