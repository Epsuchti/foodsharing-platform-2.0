package network.foodsharing.store.service;

import java.util.UUID;
import network.foodsharing.notification.service.StoreAlertAccess;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.Store;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link StoreAlertAccess} for the notification module (store alert chats). */
@Component
public class StoreAlertAccessAdapter implements StoreAlertAccess {

    private final StoreService stores;
    private final StorePermissionService permissions;

    public StoreAlertAccessAdapter(StoreService stores, StorePermissionService permissions) {
        this.stores = stores;
        this.permissions = permissions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean mayUseAutomations(SessionPrincipal actor, UUID storeId) {
        Store store = stores.require(storeId);
        return permissions.mayUseAutomations(actor, store);
    }

    @Override
    @Transactional(readOnly = true)
    public String storeName(UUID storeId) {
        return stores.require(storeId).getName();
    }
}
